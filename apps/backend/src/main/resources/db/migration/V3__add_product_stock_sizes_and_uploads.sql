ALTER TABLE products ADD COLUMN in_stock BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE product_sizes (
    product_id BIGINT NOT NULL,
    size VARCHAR(40) NOT NULL,
    CONSTRAINT fk_product_sizes_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_product_sizes_product_id ON product_sizes(product_id);
