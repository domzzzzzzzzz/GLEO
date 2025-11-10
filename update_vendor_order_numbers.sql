-- Add vendor_order_number column if not exists
ALTER TABLE orders ADD COLUMN IF NOT EXISTS vendor_order_number INTEGER;

-- Update existing orders with sequential numbers per vendor
WITH numbered_orders AS (
    SELECT id, 
           ROW_NUMBER() OVER (PARTITION BY vendor_id ORDER BY created_at, id) as order_num
    FROM orders
)
UPDATE orders 
SET vendor_order_number = numbered_orders.order_num
FROM numbered_orders
WHERE orders.id = numbered_orders.id;

-- Make the column NOT NULL after populating data
ALTER TABLE orders ALTER COLUMN vendor_order_number SET NOT NULL;
