ALTER TABLE gst_details ADD COLUMN IF NOT EXISTS gstr7_missed_count integer DEFAULT 0;

-- Widen gstr7_status only if it is still the old narrower type
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'gst_details'
          AND column_name = 'gstr7_status'
          AND character_maximum_length < 50
    ) THEN
        ALTER TABLE gst_details ALTER COLUMN gstr7_status TYPE varchar(50);
    END IF;
END$$;
