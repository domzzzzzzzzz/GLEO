package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.domain.TierPolicy;
import com.fbcorp.gleo.domain.Tier;
import com.fbcorp.gleo.service.TierService;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.repo.TierPolicyRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EventPolicyService {
    private final EventRepo eventRepo;
    private final TierPolicyRepo tierPolicyRepo;
    private final TierService tierService;

    public EventPolicyService(EventRepo eventRepo, TierPolicyRepo tierPolicyRepo, TierService tierService){
        this.eventRepo = eventRepo;
        this.tierPolicyRepo = tierPolicyRepo;
        this.tierService = tierService;
    }

    public Event get(String eventCode){
        return eventRepo.findByCode(eventCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    public boolean guestPickupEnabled(String eventCode){ return get(eventCode).isEnableGuestPickupConfirm(); }
    public boolean requirePin(String eventCode){ return get(eventCode).isRequireVendorPinForPickup(); }
    public boolean multiVendorCart(String eventCode){ return get(eventCode).isEnableMultiVendorCart(); }
    public boolean blockAddWhenOpenOrder(String eventCode){ return get(eventCode).isBlockAddWhenOpenOrder(); }
    public boolean regularOneItemPerVendor(String eventCode){ return get(eventCode).isRegularOneItemPerVendor(); }

    public TierPolicy tierPolicy(String eventCode, TierCode tierCode){
        if (tierCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tier code is required");
        }
        Event event = get(eventCode);
        Tier tier = ensureTier(event, tierCode);
        return tierPolicy(event, tier, tierCode);
    }

    public TierPolicy tierPolicy(String eventCode, Tier tier) {
        Event event = get(eventCode);
        return tierPolicy(event, tier, TierCode.fromCode(tier.getCode()));
    }

    public TierPolicy tierPolicy(String eventCode, com.fbcorp.gleo.domain.Ticket ticket) {
        if (ticket == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket is required");
        }
        Event event = get(eventCode);
        if (ticket.getTier() != null) {
            return tierPolicy(event, ticket.getTier(), TierCode.fromCode(ticket.getTier().getCode()));
        }
        TierCode code = ticket.getEffectiveTierCode();
        if (code != null) {
            return tierPolicy(eventCode, code);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket has no tier assigned.");
    }

    private TierPolicy tierPolicy(Event event, Tier tier, TierCode tierCode) {
        TierPolicy policy = tierPolicyRepo.findByEventAndTier(event, tier).orElse(null);
        if (policy == null && tierCode != null) {
            policy = tierPolicyRepo.findByEventAndTierCode(event, tierCode).orElse(null);
        }
        if (policy == null) {
            policy = new TierPolicy();
            policy.setEvent(event);
            policy.setTier(tier);
            policy.setTierCode(tierCode);
            policy.setUnlimited(true);
            return tierPolicyRepo.save(policy);
        }
        boolean changed = false;
        if (policy.getTier() == null || !policy.getTier().getId().equals(tier.getId())) {
            policy.setTier(tier);
            changed = true;
        }
        TierCode normalized = tierCode != null ? tierCode : TierCode.fromCode(tier.getCode());
        if (normalized != null && policy.getTierCode() != normalized) {
            policy.setTierCode(normalized);
            changed = true;
        }
        return changed ? tierPolicyRepo.save(policy) : policy;
    }

    public java.util.List<TierPolicy> tierPolicies(String eventCode){
        Event event = get(eventCode);
        List<TierPolicy> policies = tierPolicyRepo.findByEvent(event);
        List<TierPolicy> changed = new ArrayList<>();
        for (TierPolicy policy : policies) {
            TierCode effective = policy.getEffectiveTierCode();
            if (effective == null) {
                continue;
            }
            Tier tier = ensureTier(event, effective);
            boolean update = false;
            if (policy.getTier() == null || policy.getTier().getId() == null
                    || !policy.getTier().getId().equals(tier.getId())) {
                policy.setTier(tier);
                update = true;
            }
            TierCode normalized = TierCode.fromCode(tier.getCode());
            TierCode desired = normalized != null ? normalized : effective;
            if (policy.getTierCode() != desired) {
                policy.setTierCode(desired);
                update = true;
            }
            if (update) {
                changed.add(policy);
            }
        }
        if (!changed.isEmpty()) {
            tierPolicyRepo.saveAll(changed);
        }
        return policies;
    }

    public TierPolicy updateTierPolicy(String eventCode, TierCode tierCode, boolean unlimited, Integer maxPerVendor,
                                       boolean oneVendorOnly, boolean oneItemPerCategory, boolean singleOrderOnly,
                                       boolean lockVendorAfterOrder,
                                       Map<String, Integer> categoryLimits) {
        TierPolicy policy = tierPolicy(eventCode, tierCode);
        policy.setUnlimited(unlimited);
        policy.setMaxItemsPerVendor(unlimited ? null : maxPerVendor);
        policy.setOneVendorOnly(oneVendorOnly);
        policy.setOneItemPerCategory(oneItemPerCategory);
        policy.setSingleOrderOnly(!unlimited && singleOrderOnly);
        policy.setLockVendorAfterOrder(!unlimited && lockVendorAfterOrder);
        
        // Update category limits
        policy.getCategoryLimits().clear();
        if (categoryLimits != null && !categoryLimits.isEmpty()) {
            policy.getCategoryLimits().putAll(categoryLimits);
        }
        
        return tierPolicyRepo.save(policy);
    }
    
    public TierPolicy updateTierPolicy(String eventCode, Long tierId, boolean unlimited, Integer maxPerVendor,
                                       boolean oneVendorOnly, boolean oneItemPerCategory, boolean singleOrderOnly,
                                       boolean lockVendorAfterOrder,
                                       Map<String, Integer> categoryLimits) {
        Event event = get(eventCode);
        Tier tier = tierService.require(event, tierId);
        return updateTierPolicy(eventCode, tier, unlimited, maxPerVendor, oneVendorOnly, oneItemPerCategory, singleOrderOnly, lockVendorAfterOrder, categoryLimits);
    }

    public TierPolicy updateTierPolicy(String eventCode, Tier tier, boolean unlimited, Integer maxPerVendor,
                                       boolean oneVendorOnly, boolean oneItemPerCategory, boolean singleOrderOnly,
                                       boolean lockVendorAfterOrder,
                                       Map<String, Integer> categoryLimits) {
        TierCode tierCode = TierCode.fromCode(tier.getCode());
        TierPolicy policy = tierPolicy(eventCode, tier);
        policy.setUnlimited(unlimited);
        policy.setMaxItemsPerVendor(unlimited ? null : maxPerVendor);
        policy.setOneVendorOnly(oneVendorOnly);
        policy.setOneItemPerCategory(oneItemPerCategory);
        policy.setSingleOrderOnly(!unlimited && singleOrderOnly);
        policy.setLockVendorAfterOrder(!unlimited && lockVendorAfterOrder);
        policy.setTier(tier);
        policy.setTierCode(tierCode);

        policy.getCategoryLimits().clear();
        if (categoryLimits != null && !categoryLimits.isEmpty()) {
            policy.getCategoryLimits().putAll(categoryLimits);
        }
        return tierPolicyRepo.save(policy);
    }

    // Overload without category limits (for backward compatibility)
    public TierPolicy updateTierPolicy(String eventCode, TierCode tierCode, boolean unlimited, Integer maxPerVendor,
                                       boolean oneVendorOnly, boolean oneItemPerCategory, boolean singleOrderOnly,
                                       boolean lockVendorAfterOrder) {
        return updateTierPolicy(eventCode, tierCode, unlimited, maxPerVendor, oneVendorOnly, oneItemPerCategory, singleOrderOnly, lockVendorAfterOrder, null);
    }

    // Legacy method for backward compatibility
    public TierPolicy updateTierPolicy(String eventCode, TierCode tierCode, boolean unlimited, Integer maxPerVendor) {
        return updateTierPolicy(eventCode, tierCode, unlimited, maxPerVendor, false, false, false, false, null);
    }

    /**
     * Delete all tier policies related to the event and allow callers to remove the event itself.
     * This is a convenience helper used by admin flows to perform safe cleanup before deleting an event.
     */
    public void deleteEventPolicies(String eventCode) {
        Event event = get(eventCode);
        var policies = tierPolicyRepo.findByEvent(event);
        if (policies != null && !policies.isEmpty()) {
            tierPolicyRepo.deleteAll(policies);
        }
    }
    
    /**
     * Check if the tier policy has "one vendor only" restriction enabled
     */
    public boolean hasOneVendorOnlyPolicy(String eventCode, TierCode tierCode) {
        TierPolicy policy = tierPolicy(eventCode, tierCode);
        return policy.isOneVendorOnly();
    }

    public boolean hasOneVendorOnlyPolicy(String eventCode, com.fbcorp.gleo.domain.Ticket ticket) {
        if (ticket == null) return false;
        Tier tier = ticket.getTier();
        if (tier != null) {
            TierPolicy policy = tierPolicy(eventCode, tier);
            return policy.isOneVendorOnly();
        }
        TierCode tierCode = ticket.getEffectiveTierCode();
        if (tierCode == null) return false;
        return hasOneVendorOnlyPolicy(eventCode, tierCode);
    }

    private Tier ensureTier(Event event, TierCode tierCode) {
        String code = tierCode.name();
        String displayName = tierCode == TierCode.VIP ? "VIP" : "Regular";
        return tierService.getOrCreate(event, code, displayName, tierCode == TierCode.VIP ? 0 : 1);
    }
}
