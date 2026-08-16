package com.builtbygrain.backend.performance;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.jsontype.impl.DefaultTypeResolverBuilder;
import tools.jackson.databind.json.JsonMapper;

// Cache advice wraps transaction advice: values are stored and mutations are
// evicted only after the service transaction has committed successfully.
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE + 1)
public class CacheConfiguration implements CachingConfigurer {

    @Bean(name = "cacheManager")
    @ConditionalOnProperty(name = "app.cache.redis.enabled", havingValue = "true", matchIfMissing = true)
    CacheManager redisCacheManager(
        RedisConnectionFactory connectionFactory,
        @Value("${app.cache.redis.default-ttl:5m}") Duration defaultTtl,
        @Value("${app.cache.redis.product-detail-ttl:30s}") Duration productDetailTtl,
        @Value("${app.cache.redis.key-prefix:builtbygrain:v1::}") String keyPrefix
    ) {
        var serializer = redisValueSerializer();
        var base = RedisCacheConfiguration.defaultCacheConfig()
            .computePrefixWith(cacheName -> keyPrefix + cacheName + "::")
            .disableCachingNullValues()
            .entryTtl(defaultTtl)
            .serializeValuesWith(SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(base)
            .withInitialCacheConfigurations(Map.of(
                PublicCacheNames.PRODUCT_CARDS, base,
                PublicCacheNames.PRODUCT_DETAILS, base.entryTtl(productDetailTtl),
                PublicCacheNames.CATALOG, base,
                PublicCacheNames.STOREFRONT, base
            ))
            .build();
    }

    @Bean(name = "cacheManager")
    @ConditionalOnProperty(name = "app.cache.redis.enabled", havingValue = "false")
    CacheManager disabledCacheManager() {
        return new NoOpCacheManager();
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new ResilientCacheErrorHandler();
    }

    static GenericJacksonJsonRedisSerializer redisValueSerializer() {
        var validator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.builtbygrain.backend.")
            .allowIfSubType("java.time.")
            .allowIfSubType("java.util.")
            .build();
        JsonMapper.Builder mapper = JsonMapper.builder();
        mapper.findAndAddModules();
        mapper.setDefaultTyping(new CacheTypeResolverBuilder(validator));
        return new GenericJacksonJsonRedisSerializer(mapper.build());
    }

    private static final class CacheTypeResolverBuilder extends DefaultTypeResolverBuilder {

        CacheTypeResolverBuilder(PolymorphicTypeValidator validator) {
            super(validator, DefaultTyping.NON_FINAL_AND_RECORDS, JsonTypeInfo.As.PROPERTY);
        }

        @Override
        public boolean useForType(JavaType type) {
            return type.isJavaLangObject() || type.isContainerType() || type.isRecordType() || super.useForType(type);
        }

        @Override
        public DefaultTypeResolverBuilder withDefaultImpl(Class<?> defaultImplementation) {
            return this;
        }
    }
}
