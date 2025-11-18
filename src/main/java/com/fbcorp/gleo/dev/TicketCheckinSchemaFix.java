package com.fbcorp.gleo.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Backfills the newly introduced ticket checked-in columns so legacy rows do not contain NULLs.
 * Without this migration Hibernate would attempt to hydrate a primitive boolean with NULL and fail.
 */
@Component
public class TicketCheckinSchemaFix implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TicketCheckinSchemaFix.class);
    private final JdbcTemplate jdbcTemplate;

    public TicketCheckinSchemaFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS checked_in BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("UPDATE tickets SET checked_in = FALSE WHERE checked_in IS NULL");
            jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN checked_in SET DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN checked_in SET NOT NULL");
            jdbcTemplate.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS checked_in_at TIMESTAMP");
            log.info("Ticket check-in columns normalized successfully.");
        } catch (Exception ex) {
            log.warn("Ticket check-in schema fix skipped: {}", ex.getMessage());
        }
    }
}
