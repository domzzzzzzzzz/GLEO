-- Update existing tier policies with default values for new columns

-- Add columns if they don't exist and set defaults
ALTER TABLE tier_policies 
ADD COLUMN IF NOT EXISTS one_vendor_only BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE tier_policies 
ADD COLUMN IF NOT EXISTS one_item_per_category BOOLEAN DEFAULT FALSE NOT NULL;

-- Update any existing NULL values
UPDATE tier_policies 
SET one_vendor_only = FALSE 
WHERE one_vendor_only IS NULL;

UPDATE tier_policies 
SET one_item_per_category = FALSE 
WHERE one_item_per_category IS NULL;
