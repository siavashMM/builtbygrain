ALTER TABLE products ADD COLUMN slug VARCHAR(180);
ALTER TABLE products ADD COLUMN image_url VARCHAR(2048);

UPDATE products
SET slug = CONCAT('product-', id)
WHERE slug IS NULL;

ALTER TABLE products ALTER COLUMN slug SET NOT NULL;

CREATE UNIQUE INDEX ux_products_slug ON products(slug);
