ALTER TABLE product_variants ADD COLUMN legacy_default BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE product_variants SET legacy_default = TRUE WHERE combination_key = 'default';

ALTER TABLE product_variants DROP CONSTRAINT uq_variants_public_id;
ALTER TABLE product_variants ADD CONSTRAINT uq_variants_product_public_id UNIQUE (product_id, public_id);

ALTER TABLE product_option_values ADD COLUMN extra_label VARCHAR(160);
