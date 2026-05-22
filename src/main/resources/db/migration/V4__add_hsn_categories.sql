CREATE TABLE IF NOT EXISTS hsn_category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Ensure PRIMARY KEY exists on hsn_category.id (may be missing if table was partially created)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'hsn_category'::regclass AND contype = 'p'
    ) THEN
        ALTER TABLE hsn_category ADD PRIMARY KEY (id);
    END IF;
END$$;

ALTER TABLE gstr7_hsn_master ADD COLUMN IF NOT EXISTS category_id BIGINT;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_hsn_master_category'
    ) THEN
        ALTER TABLE gstr7_hsn_master ADD CONSTRAINT fk_hsn_master_category
            FOREIGN KEY (category_id) REFERENCES hsn_category(id) ON DELETE SET NULL;
    END IF;
END$$;

ALTER TABLE pan_hsn_config ADD COLUMN IF NOT EXISTS category_id BIGINT;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pan_hsn_category'
    ) THEN
        ALTER TABLE pan_hsn_config ADD CONSTRAINT fk_pan_hsn_category
            FOREIGN KEY (category_id) REFERENCES hsn_category(id) ON DELETE SET NULL;
    END IF;
END$$;
ALTER TABLE pan_hsn_config DROP COLUMN IF EXISTS hsn_code;

-- Seed initial "Scrap" category and link existing 7204 code
INSERT INTO hsn_category (name, description)
SELECT 'Scrap', 'Scrap and waste materials'
WHERE NOT EXISTS (SELECT 1 FROM hsn_category WHERE name = 'Scrap');

UPDATE gstr7_hsn_master SET category_id = (SELECT id FROM hsn_category WHERE name = 'Scrap' LIMIT 1)
WHERE hsn_code = '7204' AND category_id IS NULL;
