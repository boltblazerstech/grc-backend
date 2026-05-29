ALTER TABLE pan_hsn_config ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
ALTER TABLE pan_hsn_config ADD COLUMN IF NOT EXISTS category_id BIGINT;
ALTER TABLE pan_hsn_config DROP COLUMN IF EXISTS hsn_code;
