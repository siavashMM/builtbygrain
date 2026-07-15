package com.builtbygrain.backend.catalog;

import static com.builtbygrain.backend.catalog.CatalogAdminDtos.*;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.builtbygrain.backend.product.*;

@Service
public class CatalogManagementService {
    private final JdbcTemplate jdbc;
    private final ProductRepository products;
    public CatalogManagementService(JdbcTemplate jdbc, ProductRepository products) { this.jdbc = jdbc; this.products = products; }

    @Transactional(readOnly=true)
    public List<OptionDto> options(long productId) {
        requireProduct(productId);
        return jdbc.query("SELECT id, product_id, name, code, display_type, sort_order, required FROM product_options WHERE product_id=? ORDER BY sort_order,id",
            (rs,n) -> new OptionDto(rs.getLong("id"), rs.getLong("product_id"), rs.getString("name"), rs.getString("code"),
                rs.getString("display_type"), rs.getInt("sort_order"), rs.getBoolean("required"), values(rs.getLong("id"))), productId);
    }
    @Transactional
    public OptionDto createOption(long productId, OptionRequest r) {
        requireProduct(productId); long id = insert("INSERT INTO product_options(product_id,name,code,display_type,sort_order,required) VALUES(?,?,?,?,?,?)",
            productId,r.name().trim(),r.code(),r.displayType(),r.sortOrder(),r.required()); syncProduct(productId); return option(id);
    }
    @Transactional
    public OptionDto updateOption(long productId,long id,OptionRequest r) {
        requireOption(productId,id); jdbc.update("UPDATE product_options SET name=?,code=?,display_type=?,sort_order=?,required=? WHERE id=?",r.name().trim(),r.code(),r.displayType(),r.sortOrder(),r.required(),id); syncProduct(productId); return option(id);
    }
    @Transactional
    public void deleteOption(long productId,long id) {
        requireOption(productId,id); long used = jdbc.queryForObject("SELECT COUNT(*) FROM product_variant_option_values vv JOIN product_option_values ov ON ov.id=vv.option_value_id WHERE ov.option_id=?",Long.class,id);
        if(used>0) throw conflict("Option is used by variants; deactivate its values instead."); jdbc.update("DELETE FROM product_options WHERE id=?",id); syncProduct(productId);
    }
    @Transactional
    public OptionValueDto createValue(long productId,long optionId,OptionValueRequest r) {
        requireOption(productId,optionId); long id=insert("INSERT INTO product_option_values(option_id,label,code,swatch_hex,swatch_image_url,extra_label,sort_order,active) VALUES(?,?,?,?,?,?,?,?)",
            optionId,r.label().trim(),r.code(),blank(r.swatchHex()),blank(r.swatchImageUrl()),blank(r.extraLabel()),r.sortOrder(),r.active()); syncProduct(productId); return value(id);
    }
    @Transactional
    public OptionValueDto updateValue(long productId,long optionId,long id,OptionValueRequest r) {
        requireOption(productId,optionId); ensureValue(optionId,id); jdbc.update("UPDATE product_option_values SET label=?,code=?,swatch_hex=?,swatch_image_url=?,extra_label=?,sort_order=?,active=? WHERE id=?",
            r.label().trim(),r.code(),blank(r.swatchHex()),blank(r.swatchImageUrl()),blank(r.extraLabel()),r.sortOrder(),r.active(),id); syncProduct(productId); return value(id);
    }
    @Transactional
    public void deleteValue(long productId,long optionId,long id) {
        requireOption(productId,optionId); ensureValue(optionId,id); long used=jdbc.queryForObject("SELECT COUNT(*) FROM product_variant_option_values WHERE option_value_id=?",Long.class,id);
        if(used>0){jdbc.update("UPDATE product_option_values SET active=FALSE WHERE id=?",id); jdbc.update("UPDATE product_variants SET active=FALSE WHERE id IN (SELECT variant_id FROM product_variant_option_values WHERE option_value_id=?)",id);} else jdbc.update("DELETE FROM product_option_values WHERE id=?",id); syncProduct(productId);
    }

    @Transactional(readOnly=true)
    public GeneratePreview preview(long productId) {
        List<List<Long>> groups=activeRequiredValueGroups(productId); Set<String> excluded=excludedKeys(productId);
        List<String> combinations=cartesian(groups).stream().map(this::key).filter(key->!excluded.contains(key)).toList();
        Set<String> existing=combinationKeys(productId); long create=combinations.stream().filter(key->!existing.contains(key)).count();
        return new GeneratePreview(combinations.size(),combinations.size()-(int)create,(int)create);
    }
    @Transactional
    public List<VariantDto> generate(long productId,GenerateRequest r) {
        Product p=requireProduct(productId); List<List<Long>> combos=cartesian(activeRequiredValueGroups(productId));
        if(combos.isEmpty()) throw bad("Add at least one required option with active values before generating variants.");
        Set<String> wanted=new HashSet<>(); Set<String> excluded=excludedKeys(productId); Set<String> existing=combinationKeys(productId);
        long defaultPrice=r.defaultPriceCents()==null?p.getPriceCents():Math.max(0,r.defaultPriceCents());
        for(List<Long> combo:combos){String key=key(combo);if(excluded.contains(key))continue;wanted.add(key);if(!existing.contains(key)){
            String publicId="variant-"+productId+"-"+UUID.randomUUID().toString().substring(0,8);
            long variantId=insert("INSERT INTO product_variants(public_id,product_id,combination_key,regular_price_cents,stock_quantity,availability_status,active,allow_backorder,created_at,updated_at,version,legacy_default) VALUES(?,?,?,?,0,'OUT_OF_STOCK',TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,FALSE)",publicId,productId,key,defaultPrice);
            combo.forEach(valueId->jdbc.update("INSERT INTO product_variant_option_values(variant_id,option_value_id) VALUES(?,?)",variantId,valueId));
        }}
        if(Boolean.TRUE.equals(r.deactivateRemoved())) {
            if(wanted.isEmpty())jdbc.update("UPDATE product_variants SET active=FALSE,updated_at=CURRENT_TIMESTAMP WHERE product_id=? AND legacy_default=FALSE",productId);
            else jdbc.update("UPDATE product_variants SET active=FALSE,updated_at=CURRENT_TIMESTAMP WHERE product_id=? AND legacy_default=FALSE AND combination_key NOT IN ("+placeholders(wanted.size())+")",args(productId,wanted));
        }
        jdbc.update("UPDATE product_variants SET active=FALSE WHERE product_id=? AND legacy_default=TRUE",productId); syncProduct(productId); return variants(productId);
    }
    @Transactional(readOnly=true)
    public List<VariantDto> variants(long productId) {
        requireProduct(productId); return jdbc.query("SELECT * FROM product_variants WHERE product_id=? AND legacy_default=FALSE ORDER BY id",(rs,n)->variant(rs.getLong("id")),productId);
    }
    @Transactional(readOnly=true)
    public ProductConfiguration configuration(long productId) { return buildConfiguration(requireProduct(productId)); }
    @Transactional
    public VariantDto updateVariant(long productId,long variantId,VariantUpdateRequest r) {
        ensureVariant(productId,variantId); jdbc.update("UPDATE product_variants SET sku=?,regular_price_cents=?,sale_price_cents=?,stock_quantity=?,availability_status=?,active=?,allow_backorder=?,delivery_estimate=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            blank(r.sku()),r.regularPriceCents(),r.salePriceCents(),r.stockQuantity(),r.availabilityStatus(),r.active(),r.allowBackorder(),blank(r.deliveryEstimate()),variantId); syncProduct(productId); return variant(variantId);
    }
    @Transactional
    public void deleteVariant(long productId,long variantId) {
        ensureVariant(productId,variantId);
        String combinationKey=jdbc.queryForObject("SELECT combination_key FROM product_variants WHERE id=?",String.class,variantId);
        Integer excluded=jdbc.queryForObject("SELECT COUNT(*) FROM product_variant_exclusions WHERE product_id=? AND combination_key=?",Integer.class,productId,combinationKey);
        if(excluded==null||excluded==0)jdbc.update("INSERT INTO product_variant_exclusions(product_id,combination_key) VALUES(?,?)",productId,combinationKey);
        jdbc.update("DELETE FROM product_variants WHERE id=?",variantId);
        syncProduct(productId);
    }
    @Transactional
    public List<VariantDto> bulkUpdate(long productId,BulkVariantUpdateRequest r) {
        r.variantIds().forEach(id->{ensureVariant(productId,id); if(r.regularPriceCents()!=null)jdbc.update("UPDATE product_variants SET regular_price_cents=? WHERE id=?",r.regularPriceCents(),id); if(r.priceDeltaCents()!=null)jdbc.update("UPDATE product_variants SET regular_price_cents=GREATEST(0,regular_price_cents+?) WHERE id=?",r.priceDeltaCents(),id);if(r.pricePercent()!=null){Long current=jdbc.queryForObject("SELECT regular_price_cents FROM product_variants WHERE id=?",Long.class,id);jdbc.update("UPDATE product_variants SET regular_price_cents=? WHERE id=?",Math.max(0,Math.round(current*(1+r.pricePercent()/100.0))),id);} if(r.salePriceCents()!=null)jdbc.update("UPDATE product_variants SET sale_price_cents=? WHERE id=?",r.salePriceCents(),id); if(r.stockQuantity()!=null)jdbc.update("UPDATE product_variants SET stock_quantity=? WHERE id=?",Math.max(0,r.stockQuantity()),id); if(r.addStock()!=null)jdbc.update("UPDATE product_variants SET stock_quantity=GREATEST(0,stock_quantity+?) WHERE id=?",r.addStock(),id); if(r.availabilityStatus()!=null)jdbc.update("UPDATE product_variants SET availability_status=? WHERE id=?",r.availabilityStatus(),id); if(r.active()!=null)jdbc.update("UPDATE product_variants SET active=? WHERE id=?",r.active(),id); if(r.allowBackorder()!=null)jdbc.update("UPDATE product_variants SET allow_backorder=? WHERE id=?",r.allowBackorder(),id); if(r.deliveryEstimate()!=null)jdbc.update("UPDATE product_variants SET delivery_estimate=? WHERE id=?",blank(r.deliveryEstimate()),id);}); syncProduct(productId); return variants(productId);
    }

    @Transactional(readOnly=true)
    public List<ProductImageDto> images(long productId){requireProduct(productId);return jdbc.query("SELECT id,image_url,alt_text,display_order,shared,active FROM product_images WHERE product_id=? ORDER BY display_order,id",(rs,n)->{long imageId=rs.getLong(1);String url=normalize(rs.getString(2));return new ProductImageDto(imageId,url,rs.getString(3),filename(url),rs.getInt(4),rs.getBoolean(5),rs.getBoolean(6),imageUsages(productId,imageId,rs.getBoolean(5)));},productId);}
    @Transactional(readOnly=true)
    public ListingImagesDto listingImages(long productId){requireProduct(productId);return jdbc.queryForObject("SELECT listing_primary_image_id,listing_hover_image_id FROM products WHERE id=?",(rs,n)->new ListingImagesDto((Long)rs.getObject(1),(Long)rs.getObject(2)),productId);}
    @Transactional
    public ListingImagesDto assignListingImage(long productId,String role,ListingImageRequest r){requireProduct(productId);String column=switch(role){case "primary"->"listing_primary_image_id";case "hover"->"listing_hover_image_id";default->throw bad("Listing image role must be primary or hover.");};if(r.imageId()!=null){Long owner=jdbc.query("SELECT product_id FROM product_images WHERE id=? AND active=TRUE",(rs,n)->rs.getLong(1),r.imageId()).stream().findFirst().orElseThrow(()->bad("Choose an active image from this product."));if(!Objects.equals(owner,productId))throw bad("Image does not belong to this product.");}jdbc.update("UPDATE products SET "+column+"=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",r.imageId(),productId);return listingImages(productId);}
    @Transactional
    public VariantDto assignImage(long productId,long variantId,AssignVariantImageRequest r){ensureVariant(productId,variantId);Long owner=jdbc.queryForObject("SELECT product_id FROM product_images WHERE id=?",Long.class,r.imageId());if(!Objects.equals(owner,productId))throw bad("Image does not belong to this product.");if(r.primary())jdbc.update("UPDATE product_variant_images SET is_primary=FALSE WHERE variant_id=?",variantId);jdbc.update("DELETE FROM product_variant_images WHERE variant_id=? AND image_id=?",variantId,r.imageId());jdbc.update("INSERT INTO product_variant_images(variant_id,image_id,sort_order,is_primary) VALUES(?,?,?,?)",variantId,r.imageId(),r.sortOrder(),r.primary());syncProduct(productId);return variant(variantId);}
    @Transactional
    public VariantDto updateImage(long productId,long variantId,long imageId,AssignVariantImageRequest r){return assignImage(productId,variantId,new AssignVariantImageRequest(imageId,r.sortOrder(),r.primary()));}
    @Transactional
    public VariantDto removeImage(long productId,long variantId,long imageId){ensureVariant(productId,variantId);jdbc.update("DELETE FROM product_variant_images WHERE variant_id=? AND image_id=?",variantId,imageId);syncProduct(productId);return variant(variantId);}

    private OptionDto option(long id){return jdbc.queryForObject("SELECT id,product_id,name,code,display_type,sort_order,required FROM product_options WHERE id=?",(rs,n)->new OptionDto(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6),rs.getBoolean(7),values(id)),id);}
    private List<OptionValueDto> values(long optionId){return jdbc.query("SELECT id,label,code,swatch_hex,swatch_image_url,extra_label,sort_order,active FROM product_option_values WHERE option_id=? ORDER BY sort_order,id",(rs,n)->new OptionValueDto(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getInt(7),rs.getBoolean(8)),optionId);}
    private OptionValueDto value(long id){return jdbc.queryForObject("SELECT id,label,code,swatch_hex,swatch_image_url,extra_label,sort_order,active FROM product_option_values WHERE id=?",(rs,n)->new OptionValueDto(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getInt(7),rs.getBoolean(8)),id);}
    private VariantDto variant(long id){return jdbc.queryForObject("SELECT * FROM product_variants WHERE id=?",(rs,n)->{List<VariantOptionValue> ovs=jdbc.query("SELECT o.id,o.name,v.id,v.label FROM product_variant_option_values x JOIN product_option_values v ON v.id=x.option_value_id JOIN product_options o ON o.id=v.option_id WHERE x.variant_id=? ORDER BY o.sort_order,o.id",(rv,i)->new VariantOptionValue(rv.getLong(1),rv.getString(2),rv.getLong(3),rv.getString(4)),id);List<String> imgs=jdbc.query("SELECT i.image_url FROM product_variant_images x JOIN product_images i ON i.id=x.image_id WHERE x.variant_id=? ORDER BY x.is_primary DESC,x.sort_order",(ri,i)->normalize(ri.getString(1)),id);String primary=jdbc.query("SELECT i.image_url FROM product_variant_images x JOIN product_images i ON i.id=x.image_id WHERE x.variant_id=? AND x.is_primary=TRUE",(ri,i)->normalize(ri.getString(1)),id).stream().findFirst().orElse(null);return new VariantDto(id,rs.getString("public_id"),rs.getLong("product_id"),rs.getString("sku"),ovs.stream().map(VariantOptionValue::label).collect(Collectors.joining(" · ")),ovs,rs.getLong("regular_price_cents"),(Long)rs.getObject("sale_price_cents"),rs.getInt("stock_quantity"),rs.getString("availability_status"),rs.getBoolean("active"),rs.getBoolean("allow_backorder"),rs.getString("delivery_estimate"),primary,imgs);},id);}
    private List<Long> ids(String sql,Object...args){return jdbc.query(sql,(rs,n)->rs.getLong(1),args);}
    private List<List<Long>> activeRequiredValueGroups(long productId){return ids("SELECT id FROM product_options WHERE product_id=? AND required=TRUE ORDER BY sort_order,id",productId).stream().map(id->ids("SELECT id FROM product_option_values WHERE option_id=? AND active=TRUE ORDER BY sort_order,id",id)).filter(v->!v.isEmpty()).toList();}
    private List<List<Long>> cartesian(List<List<Long>> groups){List<List<Long>> result=new ArrayList<>();result.add(new ArrayList<>());for(List<Long> group:groups){List<List<Long>> next=new ArrayList<>();for(List<Long> prefix:result)for(Long v:group){List<Long> c=new ArrayList<>(prefix);c.add(v);next.add(c);}result=next;}return groups.isEmpty()?List.of():result;}
    private Set<String> combinationKeys(long productId){return new HashSet<>(jdbc.query("SELECT combination_key FROM product_variants WHERE product_id=? AND legacy_default=FALSE",(rs,n)->rs.getString(1),productId));}
    private Set<String> excludedKeys(long productId){return new HashSet<>(jdbc.query("SELECT combination_key FROM product_variant_exclusions WHERE product_id=?",(rs,n)->rs.getString(1),productId));}
    private String key(List<Long> ids){return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining("|"));}
    private void syncProduct(long productId){Product p=requireProduct(productId);p.replaceConfiguration(buildConfiguration(p));}
    private ProductConfiguration buildConfiguration(Product p){ProductConfiguration old=p.getConfiguration();List<OptionDto> managed=options(p.getId());if(managed.isEmpty())return old;List<ProductConfiguration.Option> os=managed.stream().map(o->new ProductConfiguration.Option("option-"+o.id(),o.name(),o.displayType(),o.values().stream().filter(OptionValueDto::active).map(v->new ProductConfiguration.OptionValue("value-"+v.id(),v.label(),v.swatchHex(),v.swatchImageUrl(),v.extraLabel())).toList())).toList();List<ProductConfiguration.Variant> vs=variants(p.getId()).stream().map(v->new ProductConfiguration.Variant(v.publicId(),v.sku(),v.optionValues().stream().map(x->"value-"+x.valueId()).toList(),v.regularPriceCents(),v.salePriceCents(),v.stockQuantity(),v.availabilityStatus(),isPurchasable(v),v.allowBackorder(),"PREORDER".equals(v.availabilityStatus()),v.imageUrls(),v.deliveryEstimate())).toList();return new ProductConfiguration(old.subtitle(),old.badge(),old.salePriceCents(),old.unitPriceLabel(),old.deliveryEstimate(),old.benefits(),old.specifications(),old.sections(),old.faqs(),os,vs,old.rating(),old.sizeAffectsImages());}
    private boolean isPurchasable(VariantDto variant){return variant.active() && !"OUT_OF_STOCK".equals(variant.availabilityStatus()) && !"DISCONTINUED".equals(variant.availabilityStatus());}
    private Product requireProduct(long id){return products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));}
    private void requireOption(long productId,long id){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM product_options WHERE id=? AND product_id=?",Integer.class,id,productId);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Option not found");}
    private void ensureValue(long optionId,long id){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM product_option_values WHERE id=? AND option_id=?",Integer.class,id,optionId);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Option value not found");}
    private void ensureVariant(long productId,long id){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE id=? AND product_id=? AND legacy_default=FALSE",Integer.class,id,productId);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Variant not found");}
    private long insert(String sql,Object...params){GeneratedKeyHolder kh=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement ps=c.prepareStatement(sql,new String[]{"id"});for(int i=0;i<params.length;i++)ps.setObject(i+1,params[i]);return ps;},kh);Number key=kh.getKey();if(key==null)throw new IllegalStateException("Insert did not return an id");return key.longValue();}
    private Object[] args(long productId,Set<String>wanted){List<Object>a=new ArrayList<>();a.add(productId);a.addAll(wanted);return a.toArray();}
    private String placeholders(int n){return String.join(",",Collections.nCopies(Math.max(1,n),"?"));}
    private String blank(String s){return s==null||s.isBlank()?null:s.trim();}
    private List<String> imageUsages(long productId,long imageId,boolean shared){List<String> uses=new ArrayList<>();if(shared)uses.add("Product gallery");ListingImagesDto listing=listingImages(productId);if(Objects.equals(listing.primaryImageId(),imageId))uses.add("Default card image");if(Objects.equals(listing.hoverImageId(),imageId))uses.add("Hover card image");uses.addAll(jdbc.query("SELECT COALESCE(NULLIF(v.sku,''),v.public_id) FROM product_variant_images vi JOIN product_variants v ON v.id=vi.variant_id WHERE vi.image_id=? ORDER BY v.id",(rs,n)->"Variant: "+rs.getString(1),imageId));return List.copyOf(uses);}
    private String filename(String url){if(url==null||url.isBlank())return "Image";String clean=url.split("[?#]",2)[0];int slash=clean.lastIndexOf('/');return slash>=0?clean.substring(slash+1):clean;}
    private String normalize(String u){return u!=null&&u.startsWith("/uploads/")?"/api/public"+u:u;}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
}
