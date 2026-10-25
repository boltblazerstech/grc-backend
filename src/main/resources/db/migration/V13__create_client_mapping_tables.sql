-- Table: clients
CREATE TABLE IF NOT EXISTS clients (
    id BIGSERIAL PRIMARY KEY,
    client_gstin VARCHAR(15) UNIQUE NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    logo_data TEXT,
    logo_content_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table: client_vendor_mappings
CREATE TABLE IF NOT EXISTS client_vendor_mappings (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    vendor_gstin VARCHAR(15) NOT NULL,
    mapped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_client_vendor UNIQUE (client_id, vendor_gstin)
);

CREATE INDEX IF NOT EXISTS idx_cvm_client_id
ON client_vendor_mappings(client_id);

CREATE INDEX IF NOT EXISTS idx_cvm_vendor_gstin
ON client_vendor_mappings(vendor_gstin);
