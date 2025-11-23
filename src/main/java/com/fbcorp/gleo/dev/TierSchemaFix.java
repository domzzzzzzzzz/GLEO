package com.fbcorp.gleo.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Makes legacy tier_code columns nullable so dynamic tiers (stored via tier_id) can be created
 * without failing on the enum constraint. Safe to run repeatedly.
 */
@Component
public class TierSchemaFix implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TierSchemaFix.class);
    private final JdbcTemplate jdbcTemplate;

    public TierSchemaFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE tier_policies ALTER COLUMN tier_code DROP NOT NULL");
            jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN tier_code DROP NOT NULL");
            log.info("Tier schema fix: tier_code columns set to nullable.");
        } catch (Exception ex) {
            log.warn("Tier schema fix skipped: {}", ex.getMessage());
        }
    }
}
