package com.fbcorp.gleo.dev;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Tier;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.repo.TierRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Bootstrap helper: ensures the Tier table has baseline rows (VIP/REG) for all existing events.
 * This does NOT switch the app to use dynamic tiers yet; it just prepares data for the full migration.
 */
@Configuration
public class TierBootstrap {
    private static final Logger log = LoggerFactory.getLogger(TierBootstrap.class);

    @Bean
    public ApplicationRunner seedDefaultTiers(TierRepo tierRepo, EventRepo eventRepo) {
        return args -> seed(tierRepo, eventRepo);
    }

    @Transactional
    protected void seed(TierRepo tierRepo, EventRepo eventRepo) {
        List<Event> events = eventRepo.findAll();
        for (Event event : events) {
            ensure(tierRepo, event, "VIP", "VIP", 0);
            ensure(tierRepo, event, "REG", "Regular", 1);
        }
        if (!events.isEmpty()) {
            log.info("Tier bootstrap complete for {} event(s).", events.size());
        }
    }

    private void ensure(TierRepo tierRepo, Event event, String code, String name, int order) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        String display = StringUtils.hasText(name) ? name.trim() : normalized;
        tierRepo.findByEventAndCodeIgnoreCase(event, normalized).orElseGet(() -> {
            Tier t = new Tier();
            t.setEvent(event);
            t.setCode(normalized);
            t.setName(display);
            t.setDisplayOrder(order);
            return tierRepo.save(t);
        });
    }
}
