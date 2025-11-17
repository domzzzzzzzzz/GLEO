# Database Cleanup Instructions

To delete all existing orders and start fresh, run these SQL commands in your PostgreSQL database:

```sql
-- Connect to database
-- psql -U postgres -d gleodb

-- Delete all order items first (foreign key constraint)
DELETE FROM order_items;

-- Delete all orders
DELETE FROM orders;

-- Verify deletion
SELECT COUNT(*) FROM orders;
SELECT COUNT(*) FROM order_items;
```

After cleanup, the checkout page will only show orders created by the current QR code ticket holder.
