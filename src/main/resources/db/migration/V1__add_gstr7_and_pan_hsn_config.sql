-- Add gstr7 columns to gst_details
ALTER TABLE gst_details ADD COLUMN IF NOT EXISTS gstr7_status VARCHAR(20);
ALTER TABLE gst_details ADD COLUMN IF NOT EXISTS gstr7_delay_count INTEGER DEFAULT 0;
ALTER TABLE gst_details ADD COLUMN IF NOT EXISTS gstr7_last_updated TIMESTAMP;

-- Create pan_hsn_config table
CREATE TABLE IF NOT EXISTS pan_hsn_config (
    pan VARCHAR(10) PRIMARY KEY,
    hsn_code VARCHAR(10),
    is_applicable BOOLEAN,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);
