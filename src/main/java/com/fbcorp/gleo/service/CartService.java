package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.*;
import com.fbcorp.gleo.repo.OrderRepo;
import com.fbcorp.gleo.repo.TierConsumptionRepo;
import org.springframework.stereotype.Service;

@Service
public class CartService {
    private final EventPolicyService policyService;
    private final OrderRepo orderRepo;
    private final TierConsumptionRepo tcRepo;

    public CartService(EventPolicyService policyService, OrderRepo orderRepo, TierConsumptionRepo tcRepo){
        this.policyService = policyService;
        this.orderRepo = orderRepo;
        this.tcRepo = tcRepo;
    }

    public record CheckResult(boolean allowed, String message){
        public static CheckResult allow(){ return new CheckResult(true, null); }
        public static CheckResult deny(String msg){ return new CheckResult(false, msg); }
    }

    public CheckResult canAddToCart(String eventCode, Ticket ticket, Vendor vendor, int qtySum){
        boolean blockOnOpen = policyService.blockAddWhenOpenOrder(eventCode);

        if (blockOnOpen && orderRepo.existsOpenOrder(ticket.getId(), vendor.getId())) {
            return CheckResult.deny("You have an open order with this vendor. Complete it first.");
        }

        TierPolicy tierPolicy = policyService.tierPolicy(eventCode, ticket.getTierCode());
        if (tierPolicy.hasLimit()) {
            int limit = Math.max(0, tierPolicy.getMaxItemsPerVendor());
            Integer consumed = tcRepo.consumedCount(vendor.getEvent(), ticket, vendor);
            int alreadyConsumed = consumed != null ? consumed : 0;
            if (alreadyConsumed >= limit) {
                return CheckResult.deny("Limit reached for this vendor.");
            }
            if (alreadyConsumed + qtySum > limit) {
                return CheckResult.deny("Only " + Math.max(0, limit - alreadyConsumed) + " more item(s) allowed for this vendor.");
            }
        }
        return CheckResult.allow();
    }
}
