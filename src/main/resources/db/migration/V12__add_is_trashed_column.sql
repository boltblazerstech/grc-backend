-- Add is_trashed column for soft delete functionality
ALTER TABLE gst_details ADD COLUMN is_trashed BOOLEAN DEFAULT FALSE;

-- Index for performance since many queries will now filter by is_trashed
CREATE INDEX idx_gst_details_trashed ON gst_details(is_trashed);
