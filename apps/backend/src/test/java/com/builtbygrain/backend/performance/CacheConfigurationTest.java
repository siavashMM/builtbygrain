package com.builtbygrain.backend.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.transaction.annotation.Transactional;

import com.builtbygrain.backend.catalog.CatalogDtos.CategoryPageResponse;
import com.builtbygrain.backend.catalog.CatalogDtos.CategoryResponse;
import com.builtbygrain.backend.catalog.CatalogDtos.NavigationCategory;
import com.builtbygrain.backend.product.ProductCardDto;
import com.builtbygrain.backend.product.ProductConfiguration;
import com.builtbygrain.backend.product.ProductResponse;
import com.builtbygrain.backend.storefront.StorefrontDtos.PublicCategoryDto;
import com.builtbygrain.backend.storefront.StorefrontDtos.PublicNavigationGroupDto;
import com.builtbygrain.backend.storefront.StorefrontDtos.PublicStorefrontDto;
import com.builtbygrain.backend.storefront.StorefrontDtos.StorefrontSettingsDto;

class CacheConfigurationTest {

    @Test
    void redisSerializerRoundTripsImmutablePublicDtos() {
        ProductCardDto card = new ProductCardDto(
            7L, "Oak table", "oak-table", "EUR", 12900,
            "/oak.jpg", "/oak-hover.jpg", 2L, "Tables", "tables", "furniture/tables",
            List.of(new ProductCardDto.ColorSwatch(
                11L, "Natural", "#c8a26b", null, "/oak.jpg", "/oak-hover.jpg"
            ))
        );
        PublicStorefrontDto value = new PublicStorefrontDto(
            new StorefrontSettingsDto("/hero.jpg", "Workshop", "Made well", "Built to last", Instant.parse("2026-01-02T03:04:05Z")),
            List.of(new PublicNavigationGroupDto(
                1L, "Furniture", 0,
                List.of(new PublicCategoryDto(2L, "Tables", "tables", "Solid wood", "/category/tables")),
                List.of(card)
            ))
        );

        var serializer = CacheConfiguration.redisValueSerializer();
        ProductResponse detail = new ProductResponse(
            7L, "Oak table", "oak-table", "Solid oak", 12900, "EUR", "/oak.jpg",
            List.of("/oak.jpg"), true, List.of("M"), true, ProductConfiguration.empty(),
            2L, "Tables", "tables", "furniture/tables"
        );
        CategoryResponse category = new CategoryResponse(
            2L, null, "Tables", "tables", "Solid wood", null, "/category/tables", 0, true
        );
        CategoryPageResponse categoryPage = new CategoryPageResponse(category, List.of(category), List.of(card));
        List<NavigationCategory> navigation = List.of(new NavigationCategory(
            2L, "Tables", "tables", "/category/tables", List.of()
        ));

        assertRoundTrip(serializer, value);
        assertRoundTrip(serializer, detail);
        assertRoundTrip(serializer, categoryPage);
        assertRoundTrip(serializer, navigation);
        assertRoundTrip(serializer, List.of(card));
    }

    @Test
    void invalidationPolicyDistinguishesReadsFromMutations() throws Exception {
        PublicCacheInvalidationPolicy policy = new PublicCacheInvalidationPolicy();

        assertThat(policy.isMutation(TransactionalMethods.class.getDeclaredMethod("read"))).isFalse();
        assertThat(policy.isMutation(TransactionalMethods.class.getDeclaredMethod("write"))).isTrue();
        assertThat(policy.isMutation(TransactionalMethods.class.getDeclaredMethod("plain"))).isFalse();
    }

    @Test
    void cacheManagerUsesEnvironmentSpecificPrefix() {
        CacheConfiguration configuration = new CacheConfiguration();
        RedisCacheManager manager = (RedisCacheManager) configuration.redisCacheManager(
            mock(RedisConnectionFactory.class), Duration.ofMinutes(5), Duration.ofSeconds(30), "shop:production:v1::"
        );
        manager.afterPropertiesSet();

        assertThat(manager.getCacheConfigurations().get(PublicCacheNames.PRODUCT_CARDS)
            .getKeyPrefixFor(PublicCacheNames.PRODUCT_CARDS))
            .isEqualTo("shop:production:v1::public-product-cards::");
    }

    @Test
    void cacheErrorsNeverReplaceTheDatabaseResultWithAnInfrastructureFailure() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("public-catalog");
        doThrow(new IllegalStateException("redis unavailable")).when(cache).evict("key");
        ResilientCacheErrorHandler handler = new ResilientCacheErrorHandler();

        assertThatNoException().isThrownBy(() -> handler.handleCacheGetError(
            new IllegalStateException("redis unavailable"), cache, "key"
        ));
        assertThatNoException().isThrownBy(() -> handler.handleCachePutError(
            new IllegalStateException("redis unavailable"), cache, "key", "value"
        ));
        assertThatNoException().isThrownBy(() -> handler.handleCacheEvictError(
            new IllegalStateException("redis unavailable"), cache, "key"
        ));
        assertThatNoException().isThrownBy(() -> handler.handleCacheClearError(
            new IllegalStateException("redis unavailable"), cache
        ));
    }

    private static final class TransactionalMethods {
        @Transactional(readOnly = true)
        void read() {}

        @Transactional
        void write() {}

        void plain() {}
    }

    private void assertRoundTrip(
        org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer serializer,
        Object value
    ) {
        assertThat(serializer.deserialize(serializer.serialize(value))).isEqualTo(value);
    }
}
