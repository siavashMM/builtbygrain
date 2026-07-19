package com.builtbygrain.backend.storefront;

import static com.builtbygrain.backend.storefront.StorefrontDtos.*;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/storefront")
public class AdminStorefrontController {
    private final StorefrontConfigurationService storefront;

    public AdminStorefrontController(StorefrontConfigurationService storefront) {
        this.storefront = storefront;
    }

    @GetMapping("/settings")
    public StorefrontSettingsDto settings() { return storefront.settings(); }

    @PutMapping("/settings")
    public StorefrontSettingsDto updateSettings(@Valid @RequestBody StorefrontSettingsUpdate request) {
        return storefront.updateSettings(request);
    }

    @PostMapping(path = "/settings/hero-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StorefrontSettingsDto replaceHeroImage(@RequestParam("image") MultipartFile image,
        @RequestParam("altText") String altText) {
        return storefront.replaceHeroImage(image, altText);
    }

    @GetMapping("/navigation-groups")
    public List<NavigationGroupDto> groups() { return storefront.groups(); }

    @PostMapping("/navigation-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public NavigationGroupDto createGroup(@Valid @RequestBody NavigationGroupRequest request) {
        return storefront.createGroup(request);
    }

    @PutMapping("/navigation-groups/{groupId}")
    public NavigationGroupDto updateGroup(@PathVariable long groupId,
        @Valid @RequestBody NavigationGroupRequest request) {
        return storefront.updateGroup(groupId, request);
    }

    @DeleteMapping("/navigation-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable long groupId) { storefront.deleteGroup(groupId); }

    @PatchMapping("/navigation-groups/{groupId}/activate")
    public NavigationGroupDto activateGroup(@PathVariable long groupId) {
        return storefront.setGroupActive(groupId, true);
    }

    @PatchMapping("/navigation-groups/{groupId}/deactivate")
    public NavigationGroupDto deactivateGroup(@PathVariable long groupId) {
        return storefront.setGroupActive(groupId, false);
    }

    @PutMapping("/navigation-groups/reorder")
    public List<NavigationGroupDto> reorderGroups(@Valid @RequestBody OrderedIds request) {
        return storefront.reorderGroups(request);
    }

    @PostMapping("/navigation-groups/{groupId}/categories")
    public NavigationGroupDto assignCategory(@PathVariable long groupId, @Valid @RequestBody IdReference request) {
        return storefront.assignCategory(groupId, request);
    }

    @DeleteMapping("/navigation-groups/{groupId}/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCategory(@PathVariable long groupId, @PathVariable long categoryId) {
        storefront.removeCategory(groupId, categoryId);
    }

    @PutMapping("/navigation-groups/{groupId}/categories/reorder")
    public NavigationGroupDto reorderCategories(@PathVariable long groupId, @Valid @RequestBody OrderedIds request) {
        return storefront.reorderCategories(groupId, request);
    }

    @PostMapping("/navigation-groups/{groupId}/featured-products")
    public NavigationGroupDto assignFeaturedProduct(@PathVariable long groupId, @Valid @RequestBody IdReference request) {
        return storefront.assignFeaturedProduct(groupId, request);
    }

    @DeleteMapping("/navigation-groups/{groupId}/featured-products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFeaturedProduct(@PathVariable long groupId, @PathVariable long productId) {
        storefront.removeFeaturedProduct(groupId, productId);
    }

    @PutMapping("/navigation-groups/{groupId}/featured-products/reorder")
    public NavigationGroupDto reorderFeaturedProducts(@PathVariable long groupId, @Valid @RequestBody OrderedIds request) {
        return storefront.reorderFeaturedProducts(groupId, request);
    }
}
