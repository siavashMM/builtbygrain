package com.builtbygrain.backend.catalog;

import static com.builtbygrain.backend.catalog.CatalogAdminDtos.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.builtbygrain.backend.catalog.CatalogDtos.*;
import com.builtbygrain.backend.product.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogManagementServiceTest {
    @Autowired CatalogService catalog;
    @Autowired CatalogManagementService management;
    @Autowired ProductRepository products;
    @Autowired ProductService productService;

    @Test
    void categoryLifecycleRejectsCycles() {
        Category root=catalog.create(new CategoryRequest("Shelves Test","shelves-test",null,null,null,1,true));
        Category child=catalog.create(new CategoryRequest("Floating Test","floating-test",root.getId(),null,null,0,true));
        assertThat(catalog.details(child.getId()).parentId()).isEqualTo(root.getId());
        assertThatThrownBy(()->catalog.move(root.getId(),new MoveRequest(child.getId(),0))).isInstanceOf(ResponseStatusException.class);
        catalog.update(root.getId(),new CategoryRequest("Shelving Test","shelving-test",null,"Edited",null,2,true));
        assertThat(catalog.details(root.getId()).name()).isEqualTo("Shelving Test");
    }

    @Test
    void generatesCartesianVariantsAndPreservesIndependentData() {
        Product product=products.save(new Product("Matrix Test","matrix-test","Test",3900,"EUR",null));
        OptionDto color=management.createOption(product.getId(),new OptionRequest("Color","color","COLOR_SWATCH",0,true));
        management.createValue(product.getId(),color.id(),new OptionValueRequest("Oak","oak","#AA8855",null,null,0,true));
        management.createValue(product.getId(),color.id(),new OptionValueRequest("Nussbaum","nussbaum","#553322",null,null,1,true));
        OptionDto size=management.createOption(product.getId(),new OptionRequest("Size","size","BUTTON",1,true));
        management.createValue(product.getId(),size.id(),new OptionValueRequest("50 cm","50-cm",null,null,null,0,true));
        management.createValue(product.getId(),size.id(),new OptionValueRequest("100 cm","100-cm",null,null,null,1,true));

        assertThat(management.preview(product.getId()).combinationCount()).isEqualTo(4);
        List<VariantDto> generated=management.generate(product.getId(),new GenerateRequest(3900L,true));
        assertThat(generated).extracting(VariantDto::label).containsExactlyInAnyOrder("Oak · 50 cm","Oak · 100 cm","Nussbaum · 50 cm","Nussbaum · 100 cm");
        assertThat(productService.getActiveProductBySlug("matrix-test").configuration().options())
            .extracting(ProductConfiguration.Option::name).containsExactly("Color","Size");
        VariantDto oak50=generated.stream().filter(v->v.label().equals("Oak · 50 cm")).findFirst().orElseThrow();
        management.updateVariant(product.getId(),oak50.id(),new VariantUpdateRequest("SH-OAK-50",4100,null,7,"LOW_STOCK",true,false,"2 days"));
        List<VariantDto> regenerated=management.generate(product.getId(),new GenerateRequest(9900L,true));
        VariantDto preserved=regenerated.stream().filter(v->v.id().equals(oak50.id())).findFirst().orElseThrow();
        assertThat(preserved.sku()).isEqualTo("SH-OAK-50");
        assertThat(preserved.regularPriceCents()).isEqualTo(4100);
        assertThat(preserved.stockQuantity()).isEqualTo(7);
        assertThat(products.findById(product.getId()).orElseThrow().getConfiguration().variants()
            .stream().filter(variant -> variant.id().equals(preserved.publicId())).findFirst().orElseThrow().available()).isTrue();

        management.updateVariant(product.getId(), preserved.id(), new VariantUpdateRequest(
            preserved.sku(), preserved.regularPriceCents(), null, 0, "OUT_OF_STOCK", true, false, null
        ));
        assertThat(products.findById(product.getId()).orElseThrow().getConfiguration().variants()
            .stream().filter(variant -> variant.id().equals(preserved.publicId())).findFirst().orElseThrow().available()).isFalse();
        assertThat(catalog.children("product:"+product.getId())).extracting(TreeNode::label).contains("Oak · 50 cm").doesNotContain("Default configuration");

        management.deleteVariant(product.getId(),preserved.id());
        assertThat(management.variants(product.getId())).extracting(VariantDto::id).doesNotContain(preserved.id());
        assertThat(management.preview(product.getId()).combinationCount()).isEqualTo(3);
        assertThat(management.generate(product.getId(),new GenerateRequest(3900L,true)))
            .extracting(VariantDto::label).doesNotContain("Oak · 50 cm");
        assertThat(products.findById(product.getId()).orElseThrow().getConfiguration().variants())
            .extracting(ProductConfiguration.Variant::id).doesNotContain(preserved.publicId());
    }
}
