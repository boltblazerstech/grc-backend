-- Performance indexes for commonly queried columns
CREATE INDEX IF NOT EXISTS idx_filing_gstin ON gstr7_filing_details(gstin);
CREATE INDEX IF NOT EXISTS idx_filing_gstin_status ON gstr7_filing_details(gstin, status);
CREATE INDEX IF NOT EXISTS idx_reviews_status ON gstr7_reviews(status);
CREATE INDEX IF NOT EXISTS idx_reviews_gstin ON gstr7_reviews(gstin);
CREATE INDEX IF NOT EXISTS idx_gst_details_api_error ON gst_details(api_error);
CREATE INDEX IF NOT EXISTS idx_gst_details_created_at ON gst_details(created_at);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_mobile ON users(mobile_no);
