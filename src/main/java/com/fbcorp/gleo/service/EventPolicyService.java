package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.domain.TierPolicy;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.repo.TierPolicyRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class EventPolicyService {
    private final EventRepo eventRepo;
    private final TierPolicyRepo tierPolicyRepo;

    public EventPolicyService(EventRepo eventRepo, TierPolicyRepo tierPolicyRepo){
        this.eventRepo = eventRepo;
        this.tierPolicyRepo = tierPolicyRepo;
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
        Event event = get(eventCode);
        return tierPolicyRepo.findByEventAndTierCode(event, tierCode)
                .orElseGet(() -> {
                    TierPolicy policy = new TierPolicy();
                    policy.setEvent(event);
                    policy.setTierCode(tierCode);
                    policy.setUnlimited(true);
                    return tierPolicyRepo.save(policy);
                });
    }

    public java.util.List<TierPolicy> tierPolicies(String eventCode){
        Event event = get(eventCode);
        return tierPolicyRepo.findByEvent(event);
    }

    public TierPolicy updateTierPolicy(String eventCode, TierCode tierCode, boolean unlimited, Integer maxPerVendor, 
                                       boolean oneVendorOnly, boolean oneItemPerCategory, Map<String, Integer> categoryLimits){
        TierPolicy policy = tierPolicy(eventCode, tierCode);
        policy.setUnlimited(unlimited);
        policy.setMaxItemsPerVendor(unlimited ? null : maxPerVendor);
        policy.setOneVendorOnly(oneVendorOnly);
        policy.setOneItemPerCategory(oneItemPerCategory);
        
        // Update category limits
        policy.getCategoryLimits().clear();
        if (categoryLimits != null && !categoryLimits.isEmpty()) {
            policy.getCategoryLimits().putAll(categoryLimits);
        }
        
        return tierPolicyRepo.save(policy);
    }
    
    // Overload without category limits (for backward compatibility)
    public TierPolicy updateTierPolicy(String eventCode, TierCode tierCode, boolean unlimited, Integer maxPerVendor, 
                                       boolean oneVendorOnly, boolean oneItemPerCategory){
        return updateTierPolicy(eventCode, tierCode, unlimited, maxPerVendor, oneVendorOnly, oneItemPerCategory, null);
    }

    // Legacy method for backward compatibility
    public TierPolicy updateTierPolicy(String eventCode, TierCode tierCode, boolean unlimited, Integer maxPerVendor){
        return updateTierPolicy(eventCode, tierCode, unlimited, maxPerVendor, false, false, null);
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
}
