-- First, remove any duplicate IDs that might have slipped in due to the missing primary key
DELETE FROM gstr7_filing_details a USING (
    SELECT MAX(ctid) as ctid, id
    FROM gstr7_filing_details 
    GROUP BY id HAVING COUNT(*) > 1
) b
WHERE a.id = b.id AND a.ctid <> b.ctid;

-- Add the missing Primary Key constraint
ALTER TABLE gstr7_filing_details ADD PRIMARY KEY (id);

-- Next, remove any duplicate (gstin, return_period) rows
DELETE FROM gstr7_filing_details a USING (
    SELECT MAX(id) as max_id, gstin, return_period
    FROM gstr7_filing_details 
    GROUP BY gstin, return_period HAVING COUNT(*) > 1
) b
WHERE a.gstin = b.gstin AND a.return_period = b.return_period AND a.id <> b.max_id;

-- Add the missing Unique constraint
ALTER TABLE gstr7_filing_details ADD CONSTRAINT uq_gstin_period UNIQUE (gstin, return_period);
