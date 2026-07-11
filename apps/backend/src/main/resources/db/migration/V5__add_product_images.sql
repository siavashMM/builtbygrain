CREATE TABLE product_images (
    product_id BIGINT NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_product_images_order UNIQUE (product_id, display_order)
);

INSERT INTO product_images (product_id, image_url, display_order)
SELECT id, image_url, 0
FROM products
WHERE image_url IS NOT NULL AND image_url <> '';

CREATE INDEX idx_product_images_product_id ON product_images(product_id);

