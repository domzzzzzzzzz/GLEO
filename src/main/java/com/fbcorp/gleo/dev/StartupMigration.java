package com.fbcorp.gleo.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lightweight one-off migration to introduce vendor-specific order numbering.
 * It is safe to keep; it uses IF NOT EXISTS and only updates rows with NULLs.
 */
@Component
public class StartupMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupMigration.class);
    private final JdbcTemplate jdbc;

    public StartupMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 1) Ensure column exists
            jdbc.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS vendor_order_number INTEGER");

            // 2) Backfill only NULLs with a per-vendor sequence
            jdbc.execute(
                "WITH numbered AS (" +
                "  SELECT id, ROW_NUMBER() OVER (PARTITION BY vendor_id ORDER BY created_at, id) AS n" +
                "  FROM orders" +
                ") " +
                "UPDATE orders o SET vendor_order_number = n.n " +
                "FROM numbered n WHERE o.id = n.id AND o.vendor_order_number IS NULL"
            );

            // 3) Enforce NOT NULL if all rows populated (ignore if fails)
            try {
                jdbc.execute("ALTER TABLE orders ALTER COLUMN vendor_order_number SET NOT NULL");
            } catch (Exception ignore) {
                // If some rows still NULL (rare), keep column nullable; app will continue to work
            }

            log.info("Startup migration for vendor_order_number completed.");
        } catch (Exception ex) {
            log.warn("Startup migration skipped/failed: {}", ex.getMessage());
        }
    }
}
