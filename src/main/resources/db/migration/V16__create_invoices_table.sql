CREATE TABLE IF NOT EXISTS generated_invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    company_name VARCHAR(255),
    billing_month INT,
    billing_year INT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_generated_invoices_number ON generated_invoices(invoice_number);
