-- 1. Drop old composite constraint if present
ALTER TABLE client_vendor_mappings DROP CONSTRAINT IF EXISTS uq_client_vendor;

-- 2. Deduplicate existing mappings if any vendor is mapped to multiple clients (keep the latest row)
DELETE FROM client_vendor_mappings a USING client_vendor_mappings b
WHERE a.id < b.id AND a.vendor_gstin = b.vendor_gstin;

-- 3. Add UNIQUE constraint on vendor_gstin so a vendor can be mapped to ONE client only
ALTER TABLE client_vendor_mappings DROP CONSTRAINT IF EXISTS uq_vendor_gstin;
ALTER TABLE client_vendor_mappings ADD CONSTRAINT uq_vendor_gstin UNIQUE (vendor_gstin);
