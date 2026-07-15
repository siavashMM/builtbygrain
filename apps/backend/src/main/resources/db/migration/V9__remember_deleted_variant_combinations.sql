CREATE TABLE product_variant_exclusions (
    product_id BIGINT NOT NULL,
    combination_key VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, combination_key),
    CONSTRAINT fk_variant_exclusions_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
