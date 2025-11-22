package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.*;
import com.fbcorp.gleo.repo.OrderRepo;
import com.fbcorp.gleo.repo.TierConsumptionRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartService {
    private final EventPolicyService policyService;
    private final OrderRepo orderRepo;
    private final TierConsumptionRepo tcRepo;

    public CartService(EventPolicyService policyService, OrderRepo orderRepo, TierConsumptionRepo tcRepo) {
        this.policyService = policyService;
        this.orderRepo = orderRepo;
        this.tcRepo = tcRepo;
    }

    public record CheckResult(boolean allowed, String message) {
        public static CheckResult allow() {
            return new CheckResult(true, null);
        }

        public static CheckResult deny(String msg) {
            return new CheckResult(false, msg);
        }
    }

    public CheckResult canAddToCart(String eventCode, Ticket ticket, Vendor vendor, int qtySum) {
        boolean blockOnOpen = policyService.blockAddWhenOpenOrder(eventCode);

        if (blockOnOpen && orderRepo.existsOpenOrder(ticket.getId(), vendor.getId())) {
            return CheckResult.deny(
                    " You have an open order with this vendor. Please complete it first before ordering more items.");
        }

        TierPolicy tierPolicy = policyService.tierPolicy(eventCode, ticket.getTierCode());

        // New policy: allow only ONE order total for this ticket
        if (tierPolicy.isSingleOrderOnly() && orderRepo.existsByTicket_Id(ticket.getId())) {
            return CheckResult.deny("This ticket already placed an order. Policy allows only one order total.");
        }
        // If policy says lock vendor after first order with it
        if (tierPolicy.isLockVendorAfterOrder() &&
                orderRepo.existsByTicket_IdAndVendor_Id(ticket.getId(), vendor.getId())) {
            return CheckResult.deny("You already placed an order with " + vendor.getName() + ". This vendor is locked for this ticket.");
        }

        // Check if tier is locked to a specific vendor
        if (tierPolicy.hasVendorRestriction()) {
            TierConsumption consumption = tcRepo.findByEventAndTicket(vendor.getEvent(), ticket).orElse(null);

            // First check: if already completed an order and locked to a vendor
            if (consumption != null && consumption.getLockedVendor() != null) {
                if (!consumption.getLockedVendor().getId().equals(vendor.getId())) {
                    return CheckResult.deny("Your " + ticket.getTierCode() + " tier is restricted to "
                            + consumption.getLockedVendor().getName() + " only.");
                }
            }

            // Second check: if has ANY orders (pending/ready) from another vendor - BLOCK
            // immediately
            List<Order> existingOrders = orderRepo.findByTicketOrderByCreatedAtDesc(ticket);
            if (!existingOrders.isEmpty()) {
                for (Order order : existingOrders) {
                    if (!order.getVendor().getId().equals(vendor.getId())) {
                        return CheckResult.deny("Your " + ticket.getTierCode() + " tier is restricted to "
                                + order.getVendor().getName() + " only. Complete your existing order first.");
                    }
                }
            }
        }

        return CheckResult.allow();
    }

    /**
     * Check if items can be added to cart based on category restrictions.
     * 
     * @param items List of items with their categories and quantities
     */
    public CheckResult canAddItemsWithCategories(String eventCode, Ticket ticket, Vendor vendor,
            List<CategoryItem> items) {
        // Skip maxItemsPerVendor checks; rely on category limits only

        TierPolicy tierPolicy = policyService.tierPolicy(eventCode, ticket.getTierCode());

        // Check category restrictions
        if (tierPolicy.hasCategoryRestriction()) {
            TierConsumption consumption = tcRepo.findByEventAndTicket(vendor.getEvent(), ticket)
                    .orElse(null);

            for (CategoryItem item : items) {
                if (item.category() == null || item.category().isBlank())
                    continue;

                int alreadyConsumed = consumption != null ? consumption.getCategoryConsumption(item.category()) : 0;

                Integer categoryLimit = tierPolicy.getCategoryLimit(item.category());
                if (categoryLimit != null) {
                    if (alreadyConsumed >= categoryLimit) {
                        return CheckResult.deny("Limit reached for " + item.category()
                                + ". Your tier allows " + categoryLimit + " item"
                                + (categoryLimit == 1 ? "" : "s") + " in this category.");
                    }
                    if (alreadyConsumed + item.quantity() > categoryLimit) {
                        int remaining = Math.max(0, categoryLimit - alreadyConsumed);
                        return CheckResult.deny("You can add only " + remaining + " more item"
                                + (remaining == 1 ? "" : "s") + " from category '" + item.category()
                                + "' (limit: " + categoryLimit + ").");
                    }
                }
            }
        }

        return CheckResult.allow();
    }

    public record CategoryItem(String category, int quantity) {
    }
}
