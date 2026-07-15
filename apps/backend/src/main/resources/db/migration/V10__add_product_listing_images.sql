ALTER TABLE products ADD COLUMN listing_primary_image_id BIGINT;
ALTER TABLE products ADD COLUMN listing_hover_image_id BIGINT;

ALTER TABLE product_images
    ADD CONSTRAINT uq_product_images_id_product UNIQUE (id, product_id);

ALTER TABLE products
    ADD CONSTRAINT fk_products_listing_primary_image
    FOREIGN KEY (listing_primary_image_id, id) REFERENCES product_images(id, product_id);

ALTER TABLE products
    ADD CONSTRAINT fk_products_listing_hover_image
    FOREIGN KEY (listing_hover_image_id, id) REFERENCES product_images(id, product_id);

CREATE INDEX idx_products_listing_primary_image ON products(listing_primary_image_id);
CREATE INDEX idx_products_listing_hover_image ON products(listing_hover_image_id);
