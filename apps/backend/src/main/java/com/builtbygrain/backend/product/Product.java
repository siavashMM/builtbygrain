package com.builtbygrain.backend.product;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(length = 1000)
    private String description;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", nullable = false, length = 2048)
    @OrderColumn(name = "display_order")
    private List<String> imageUrls = new ArrayList<>();

    @Column(nullable = false)
    private long priceCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean inStock = true;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_sizes", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "size", nullable = false, length = 40)
    private List<String> sizes = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String name, String slug, String description, long priceCents, String currency, String imageUrl) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.priceCents = priceCents;
        this.currency = currency;
        if (imageUrl != null && !imageUrl.isBlank()) {
            this.imageUrls.add(imageUrl);
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrls.isEmpty() ? null : imageUrls.getFirst();
    }

    public List<String> getImageUrls() {
        return List.copyOf(imageUrls);
    }

    public long getPriceCents() {
        return priceCents;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isInStock() {
        return inStock;
    }

    public List<String> getSizes() {
        return List.copyOf(sizes);
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateFrom(ProductRequest request) {
        name = request.name();
        slug = request.slug();
        description = request.description();
        priceCents = request.priceCents();
        currency = request.currency();
        if (request.inStock() != null) {
            inStock = request.inStock();
        }
        sizes.clear();
        if (request.sizes() != null) {
            sizes.addAll(request.sizes());
        }
        if (request.active() != null) {
            active = request.active();
        }
    }

    public void addImageUrl(String imageUrl) {
        imageUrls.add(imageUrl);
    }

    public String removeImage(int index) {
        return imageUrls.remove(index);
    }

    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
    }
}
