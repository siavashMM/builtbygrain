package com.builtbygrain.backend.catalog;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(columnNames = {"parent_id", "slug"}))
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id")
    private Category parent;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 180) private String slug;
    @Column(length = 1000) private String description;
    @Column(length = 2048) private String imageUrl;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected Category() {}

    /**
     * Reference to the default category created by the catalog migration.
     * Keeps legacy code that constructs Product directly compatible while all
     * application writes still resolve and assign the managed category.
     */
    public static Category defaultReference() {
        Category category = new Category();
        category.id = 1L;
        return category;
    }

    public Category(String name, String slug, Category parent, int sortOrder) {
        this.name = name; this.slug = slug; this.parent = parent; this.sortOrder = sortOrder;
    }
    @PrePersist void createTimestamps() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public Category getParent() { return parent; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public void update(String name, String slug, String description, String imageUrl, boolean active) {
        this.name = name; this.slug = slug; this.description = description; this.imageUrl = imageUrl; this.active = active;
    }
    public void moveTo(Category parent, int sortOrder) { this.parent = parent; this.sortOrder = sortOrder; }
}
