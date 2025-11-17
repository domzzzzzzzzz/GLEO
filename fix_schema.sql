-- Fix TierConsumption schema for new restrictions

-- Drop old tables
DROP TABLE IF EXISTS tier_consumption_categories CASCADE;
DROP TABLE IF EXISTS tier_consumption_vendors CASCADE;
DROP TABLE IF EXISTS tier_consumption CASCADE;

-- The tables will be recreated automatically by Hibernate on next startup
-- with the correct schema including:
-- - locked_vendor_id column
-- - one_vendor_only and one_item_per_category columns in tier_policies
-- - category and vendor consumption tracking tables
