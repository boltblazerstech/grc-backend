-- V21: Allow multiple clients to be linked to the same supplier/vendor.
-- Drops the single-vendor constraint uq_vendor_gstin and replaces it with
-- composite unique constraint on (client_id, vendor_gstin).

ALTER TABLE client_vendor_mappings DROP CONSTRAINT IF EXISTS uq_vendor_gstin;
ALTER TABLE client_vendor_mappings DROP CONSTRAINT IF EXISTS uq_client_vendor;
ALTER TABLE client_vendor_mappings ADD CONSTRAINT uq_client_vendor UNIQUE (client_id, vendor_gstin);
