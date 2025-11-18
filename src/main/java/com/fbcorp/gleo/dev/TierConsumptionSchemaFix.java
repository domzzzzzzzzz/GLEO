package com.fbcorp.gleo.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Drops the legacy NOT NULL tier_consumption.vendor_id column that conflicts with the
 * new {@link com.fbcorp.gleo.domain.TierConsumption} schema. Without removing it,
 * inserts fail whenever Hibernate tries to persist a new TierConsumption row.
 */
@Component
public class TierConsumptionSchemaFix implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TierConsumptionSchemaFix.class);
    private final JdbcTemplate jdbcTemplate;

    public TierConsumptionSchemaFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Boolean hasLegacyColumn = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'tier_consumption'
                      AND column_name = 'vendor_id'
                )
                """,
                Boolean.class
            );

            if (Boolean.TRUE.equals(hasLegacyColumn)) {
                log.warn("Dropping legacy tier_consumption.vendor_id column to rebuild derived data.");
                jdbcTemplate.execute("ALTER TABLE tier_consumption DROP COLUMN IF EXISTS vendor_id");
            }
        } catch (Exception ex) {
            log.warn("TierConsumption schema fix skipped: {}", ex.getMessage());
        }
    }
}
