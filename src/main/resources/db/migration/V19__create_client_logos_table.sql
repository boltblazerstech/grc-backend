-- Table: client_logos
-- Persistent storage mapping client logo by its 15-character GSTIN.
-- Automatically preserved and reused across all invoices, client imports, and reports.
CREATE TABLE IF NOT EXISTS client_logos (
    id BIGSERIAL PRIMARY KEY,
    gstin VARCHAR(15) UNIQUE NOT NULL,
    client_name VARCHAR(255),
    logo_data TEXT NOT NULL,
    logo_content_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_client_logos_gstin ON client_logos(gstin);

-- Seed existing logos from clients table into client_logos if present
INSERT INTO client_logos (gstin, client_name, logo_data, logo_content_type, created_at, updated_at)
SELECT UPPER(TRIM(client_gstin)), client_name, logo_data, logo_content_type, created_at, updated_at
FROM clients
WHERE logo_data IS NOT NULL AND TRIM(logo_data) != ''
ON CONFLICT (gstin) DO UPDATE
SET logo_data = EXCLUDED.logo_data,
    logo_content_type = EXCLUDED.logo_content_type,
    updated_at = CURRENT_TIMESTAMP;
