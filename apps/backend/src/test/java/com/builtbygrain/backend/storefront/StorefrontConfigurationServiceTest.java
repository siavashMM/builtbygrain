package com.builtbygrain.backend.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.builtbygrain.backend.catalog.CatalogDtos.CategoryRequest;
import com.builtbygrain.backend.catalog.CatalogDtos.CategoryResponse;
import com.builtbygrain.backend.catalog.CatalogService;
import com.builtbygrain.backend.product.Product;
import com.builtbygrain.backend.product.ProductRepository;
import com.builtbygrain.backend.storefront.StorefrontDtos.IdReference;
import com.builtbygrain.backend.storefront.StorefrontDtos.NavigationGroupRequest;
import com.builtbygrain.backend.storefront.StorefrontDtos.OrderedIds;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StorefrontConfigurationServiceTest {
    @Autowired StorefrontConfigurationService storefront;
    @Autowired CatalogService catalog;
    @Autowired ProductRepository products;

    @Test
    void emptyConfigurationHasCurrentHomepageCopyAsDefaults() {
        assertThat(storefront.settings().heroImageUrl()).isNull();
        assertThat(storefront.settings().heroHeading())
            .isEqualTo("Handcrafted wooden goods, shaped for everyday use.");
        assertThat(storefront.settings().heroSupportingText())
            .isEqualTo("Thoughtfully made pieces for calmer desks, organised homes and durable everyday rituals.");
        assertThat(storefront.groups()).isEmpty();
        assertThat(storefront.publicStorefront().navigationGroups()).isEmpty();
    }

    @Test
    void assignmentsReferenceOrderedCategoriesWithoutChangingTheirHierarchy() {
        CategoryResponse parent = category("Storefront Parent", "storefront-parent", null, true);
        CategoryResponse child = category("Storefront Child", "storefront-child", parent.id(), true);
        long groupId = storefront.createGroup(new NavigationGroupRequest("Shop", true)).id();

        storefront.assignCategory(groupId, new IdReference(child.id()));
        storefront.assignCategory(groupId, new IdReference(parent.id()));
        var reordered = storefront.reorderCategories(groupId, new OrderedIds(java.util.List.of(parent.id(), child.id())));

        assertThat(reordered.categories()).extracting(item -> item.id()).containsExactly(parent.id(), child.id());
        assertThat(reordered.categories().get(1).parentId()).isEqualTo(parent.id());
        assertThat(catalog.details(child.id()).name()).isEqualTo("Storefront Child");
    }

    @Test
    void duplicateAssignmentsAndFourthFeaturedProductAreConflicts() {
        long groupId = storefront.createGroup(new NavigationGroupRequest("Featured", true)).id();
        CategoryResponse category = category("Unique Assignment", "unique-assignment", null, true);
        storefront.assignCategory(groupId, new IdReference(category.id()));
        assertThatThrownBy(() -> storefront.assignCategory(groupId, new IdReference(category.id())))
            .isInstanceOf(StorefrontApiException.class).hasMessageContaining("already assigned");

        for (int index = 1; index <= 3; index++) {
            Product product = products.save(new Product("Featured " + index, "storefront-featured-" + index, null, 1000, "EUR", null));
            storefront.assignFeaturedProduct(groupId, new IdReference(product.getId()));
        }
        Product fourth = products.save(new Product("Featured 4", "storefront-featured-4", null, 1000, "EUR", null));
        assertThatThrownBy(() -> storefront.assignFeaturedProduct(groupId, new IdReference(fourth.getId())))
            .isInstanceOf(StorefrontApiException.class).hasMessageContaining("at most three");
    }

    @Test
    void publicAggregateOmitsInactiveCategoriesAndInactiveOrArchivedProducts() {
        long groupId = storefront.createGroup(new NavigationGroupRequest("Public group", true)).id();
        CategoryResponse visible = category("Visible Config", "visible-config", null, true);
        CategoryResponse hidden = category("Hidden Config", "hidden-config", null, false);
        storefront.assignCategory(groupId, new IdReference(visible.id()));
        storefront.assignCategory(groupId, new IdReference(hidden.id()));

        Product active = products.save(new Product("Public Featured", "public-storefront-featured", null, 2500, "EUR", "/public.jpg"));
        Product archived = new Product("Archived Featured", "archived-storefront-featured", null, 2600, "EUR", null);
        archived.deactivate();
        archived = products.save(archived);
        storefront.assignFeaturedProduct(groupId, new IdReference(active.getId()));
        storefront.assignFeaturedProduct(groupId, new IdReference(archived.getId()));

        var result = storefront.publicStorefront().navigationGroups().getFirst();
        assertThat(result.categories()).extracting(item -> item.id()).containsExactly(visible.id());
        assertThat(result.featuredProducts()).extracting(item -> item.id()).containsExactly(active.getId());
    }

    @Test
    void validatesEveryReferenceAndRequiresCompleteReorderSets() {
        long groupId = storefront.createGroup(new NavigationGroupRequest("Validation", false)).id();
        assertThatThrownBy(() -> storefront.assignCategory(groupId, new IdReference(Long.MAX_VALUE)))
            .isInstanceOf(StorefrontApiException.class).hasMessage("Category not found.");
        assertThatThrownBy(() -> storefront.assignFeaturedProduct(groupId, new IdReference(Long.MAX_VALUE)))
            .isInstanceOf(StorefrontApiException.class).hasMessage("Product not found.");
        assertThatThrownBy(() -> storefront.reorderGroups(new OrderedIds(java.util.List.of())))
            .isInstanceOf(StorefrontApiException.class).hasMessageContaining("every current navigation groups");
    }

    private CategoryResponse category(String name, String slug, Long parentId, boolean active) {
        return catalog.create(new CategoryRequest(name, slug, parentId, null, null, 0, active));
    }
}
