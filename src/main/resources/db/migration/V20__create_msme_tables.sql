-- Table: msme_details
-- Permanent database storage for vendor MSME (Udyam) details fetched via PAN.
-- Follows Database-First rule: Once stored, reused permanently with zero external API cost.
CREATE TABLE IF NOT EXISTS msme_details (
    id BIGSERIAL PRIMARY KEY,
    gstin VARCHAR(15) UNIQUE NOT NULL,
    pan_number VARCHAR(10) NOT NULL,
    msme_status VARCHAR(50) NOT NULL DEFAULT 'DATA_UNAVAILABLE',
    udyam_number VARCHAR(100),
    enterprise_name VARCHAR(255),
    enterprise_type VARCHAR(50),
    organization_type VARCHAR(100),
    major_activity VARCHAR(100),
    date_of_incorporation VARCHAR(50),
    date_of_commencement VARCHAR(50),
    date_of_udyam_registration VARCHAR(50),
    social_category VARCHAR(50),
    dic VARCHAR(100),
    msme_di VARCHAR(100),
    state VARCHAR(100),
    district VARCHAR(100),
    pincode VARCHAR(20),
    mobile VARCHAR(50),
    email VARCHAR(100),
    raw_response_json TEXT,
    last_fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_manual BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_msme_details_gstin ON msme_details(gstin);
CREATE INDEX IF NOT EXISTS idx_msme_details_pan ON msme_details(pan_number);

-- Table: msme_api_logs
-- 60-day audit log tracking daily MSME API hits and enforcing the 50 hits/day auto cap.
CREATE TABLE IF NOT EXISTS msme_api_logs (
    id BIGSERIAL PRIMARY KEY,
    call_date DATE NOT NULL,
    call_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    call_type VARCHAR(20) NOT NULL, -- 'AUTO' or 'MANUAL'
    gstin VARCHAR(15),
    pan_number VARCHAR(10),
    http_status INT,
    status VARCHAR(50) NOT NULL, -- 'SUCCESS', 'FAILED', 'LIMIT_REACHED', 'NO_RECORD'
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_msme_api_logs_call_date ON msme_api_logs(call_date);
CREATE INDEX IF NOT EXISTS idx_msme_api_logs_type_date ON msme_api_logs(call_type, call_date);
