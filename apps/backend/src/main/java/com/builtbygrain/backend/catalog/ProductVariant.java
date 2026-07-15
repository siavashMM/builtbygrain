package com.builtbygrain.backend.catalog;

import java.time.Instant;
import jakarta.persistence.*;
import com.builtbygrain.backend.product.Product;

@Entity
@Table(name = "product_variants", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "combination_key"}))
public class ProductVariant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 180) private String publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Product product;
    @Column(length = 120) private String sku;
    @Column(nullable = false, length = 1000) private String combinationKey;
    @Column(nullable = false) private long regularPriceCents;
    private Long salePriceCents;
    @Column(nullable = false) private int stockQuantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AvailabilityStatus availabilityStatus;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private boolean allowBackorder;
    @Column(length = 160) private String deliveryEstimate;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;
    protected ProductVariant() {}
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public String getCombinationKey() { return combinationKey; }
    public long getRegularPriceCents() { return regularPriceCents; }
    public Long getSalePriceCents() { return salePriceCents; }
    public int getStockQuantity() { return stockQuantity; }
    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public boolean isActive() { return active; }
    public boolean isAllowBackorder() { return allowBackorder; }
    public String getDeliveryEstimate() { return deliveryEstimate; }
    public void update(String sku, long price, Long salePrice, int stock, AvailabilityStatus availability, boolean active, boolean backorder, String delivery) {
        this.sku = sku; this.regularPriceCents = price; this.salePriceCents = salePrice; this.stockQuantity = stock;
        this.availabilityStatus = availability; this.active = active; this.allowBackorder = backorder; this.deliveryEstimate = delivery;
    }
    @PrePersist void createTimestamps() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public enum AvailabilityStatus { IN_STOCK, LOW_STOCK, OUT_OF_STOCK, BACKORDER, PREORDER, DISCONTINUED }
}
