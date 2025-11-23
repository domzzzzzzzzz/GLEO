package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Tier;
import com.fbcorp.gleo.repo.TierRepo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TierService {
    private final TierRepo tierRepo;

    public TierService(TierRepo tierRepo) {
        this.tierRepo = tierRepo;
    }

    public List<Tier> list(Event event) {
        return tierRepo.findByEventOrderByDisplayOrderAscNameAsc(event);
    }

    public Tier getOrCreate(Event event, String code, String name, Integer order) {
        String normalized = normalizeCode(code);
        String display = StringUtils.hasText(name) ? name.trim() : normalized;
        return tierRepo.findByEventAndCodeIgnoreCase(event, normalized)
                .orElseGet(() -> {
                    Tier t = new Tier();
                    t.setEvent(event);
                    t.setCode(normalized);
                    t.setName(display);
                    t.setDisplayOrder(order == null ? 999 : order);
                    return tierRepo.save(t);
                });
    }

    public Tier require(Event event, String code) {
        return tierRepo.findByEventAndCodeIgnoreCase(event, normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Tier not found: " + code));
    }

    public Tier require(Event event, Long tierId) {
        return tierRepo.findById(tierId)
                .filter(t -> t.getEvent().getId().equals(event.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Tier not found for this event"));
    }

    public void delete(Tier tier) {
        tierRepo.delete(tier);
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Tier code is required");
        }
        return normalized;
    }
}
