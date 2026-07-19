package com.builtbygrain.backend.admin;

import static com.builtbygrain.backend.catalog.CatalogAdminDtos.*;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.builtbygrain.backend.catalog.CatalogManagementService;

@RestController
@RequestMapping("/api/admin/products/{productId}")
public class AdminCatalogManagementController {
    private final CatalogManagementService catalog;
    public AdminCatalogManagementController(CatalogManagementService catalog){this.catalog=catalog;}

    @GetMapping("/options") public List<OptionDto> options(@PathVariable long productId){return catalog.options(productId);}
    @PostMapping("/options") @ResponseStatus(HttpStatus.CREATED) public OptionDto createOption(@PathVariable long productId,@Valid @RequestBody OptionRequest r){return catalog.createOption(productId,r);}
    @PutMapping("/options/{optionId}") public OptionDto updateOption(@PathVariable long productId,@PathVariable long optionId,@Valid @RequestBody OptionRequest r){return catalog.updateOption(productId,optionId,r);}
    @DeleteMapping("/options/{optionId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteOption(@PathVariable long productId,@PathVariable long optionId){catalog.deleteOption(productId,optionId);}
    @PostMapping("/options/{optionId}/values") @ResponseStatus(HttpStatus.CREATED) public OptionValueDto createValue(@PathVariable long productId,@PathVariable long optionId,@Valid @RequestBody OptionValueRequest r){return catalog.createValue(productId,optionId,r);}
    @PutMapping("/options/{optionId}/values/{valueId}") public OptionValueDto updateValue(@PathVariable long productId,@PathVariable long optionId,@PathVariable long valueId,@Valid @RequestBody OptionValueRequest r){return catalog.updateValue(productId,optionId,valueId,r);}
    @DeleteMapping("/options/{optionId}/values/{valueId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteValue(@PathVariable long productId,@PathVariable long optionId,@PathVariable long valueId){catalog.deleteValue(productId,optionId,valueId);}

    @GetMapping("/variants") public List<VariantDto> variants(@PathVariable long productId){return catalog.variants(productId);}
    @GetMapping("/variants/generate-preview") public GeneratePreview preview(@PathVariable long productId){return catalog.preview(productId);}
    @PostMapping("/variants/generate") public List<VariantDto> generate(@PathVariable long productId,@Valid @RequestBody GenerateRequest r){return catalog.generate(productId,r);}
    @PutMapping("/variants/{variantId}") public VariantDto updateVariant(@PathVariable long productId,@PathVariable long variantId,@Valid @RequestBody VariantUpdateRequest r){return catalog.updateVariant(productId,variantId,r);}
    @DeleteMapping("/variants/{variantId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteVariant(@PathVariable long productId,@PathVariable long variantId){catalog.deleteVariant(productId,variantId);}
    @PostMapping("/variants/bulk-update") public List<VariantDto> bulk(@PathVariable long productId,@Valid @RequestBody BulkVariantUpdateRequest r){return catalog.bulkUpdate(productId,r);}
    @PatchMapping("/variants/{variantId}/status") public VariantDto status(@PathVariable long productId,@PathVariable long variantId,@Valid @RequestBody StatusRequest r){VariantDto v=catalog.variants(productId).stream().filter(x->x.id()==variantId).findFirst().orElseThrow();return catalog.updateVariant(productId,variantId,new VariantUpdateRequest(v.sku(),v.regularPriceCents(),v.salePriceCents(),v.stockQuantity(),v.availabilityStatus(),r.active(),v.allowBackorder(),v.deliveryEstimate()));}

    @GetMapping("/images") public List<ProductImageDto> images(@PathVariable long productId){return catalog.images(productId);}
    @DeleteMapping("/catalog-images/{imageId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteImage(@PathVariable long productId,@PathVariable long imageId){catalog.deleteImage(productId,imageId);}
    @GetMapping("/listing-images") public ListingImagesDto listingImages(@PathVariable long productId){return catalog.listingImages(productId);}
    @PutMapping("/listing-images/{role}") public ListingImagesDto listingImage(@PathVariable long productId,@PathVariable String role,@RequestBody ListingImageRequest r){return catalog.assignListingImage(productId,role,r);}
    @PostMapping("/variants/{variantId}/images") public VariantDto assign(@PathVariable long productId,@PathVariable long variantId,@Valid @RequestBody AssignVariantImageRequest r){return catalog.assignImage(productId,variantId,r);}
    @PatchMapping("/variants/{variantId}/images/{imageId}") public VariantDto updateImage(@PathVariable long productId,@PathVariable long variantId,@PathVariable long imageId,@Valid @RequestBody AssignVariantImageRequest r){return catalog.updateImage(productId,variantId,imageId,r);}
    @DeleteMapping("/variants/{variantId}/images/{imageId}") public VariantDto removeImage(@PathVariable long productId,@PathVariable long variantId,@PathVariable long imageId){return catalog.removeImage(productId,variantId,imageId);}
}
