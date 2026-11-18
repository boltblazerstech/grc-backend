ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS customer_gstin VARCHAR(20);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS customer_address TEXT;
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS nature_of_supply VARCHAR(255);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS sac_code VARCHAR(50);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS invoice_date VARCHAR(50);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS billing_period VARCHAR(50);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS item_description VARCHAR(255);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS price NUMERIC(12, 2);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5, 2);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS final_rate NUMERIC(12, 2);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12, 2);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS total_amount NUMERIC(12, 2);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS is_inter_state BOOLEAN;
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS amount_in_words VARCHAR(500);
ALTER TABLE generated_invoices ADD COLUMN IF NOT EXISTS json_data TEXT;

CREATE INDEX IF NOT EXISTS idx_generated_invoices_period ON generated_invoices(billing_year, billing_month);
