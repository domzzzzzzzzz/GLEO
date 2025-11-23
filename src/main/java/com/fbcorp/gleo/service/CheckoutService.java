package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.Order;
import com.fbcorp.gleo.domain.OrderItem;
import com.fbcorp.gleo.domain.OrderStatus;
import com.fbcorp.gleo.domain.TierConsumption;
import com.fbcorp.gleo.domain.TierPolicy;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.OrderRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.repo.TierConsumptionRepo;
import com.fbcorp.gleo.service.EventPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CheckoutService {

    public record CartLine(Long itemId, int qty) {}

    public static class CheckoutResult {
        public final List<Order> orders = new ArrayList<>();
        public final Map<Long, String> rejectedByVendor = new LinkedHashMap<>();
    }

    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final OrderRepo orderRepo;
    private final CartService cartService;
    private final OrderService orderService;
    private final TierConsumptionRepo tierConsumptionRepo;
    private final EventPolicyService policyService;

    public CheckoutService(VendorRepo vendorRepo,
                           MenuItemRepo menuItemRepo,
                           OrderRepo orderRepo,
                           CartService cartService,
                           OrderService orderService,
                           TierConsumptionRepo tierConsumptionRepo,
                           EventPolicyService policyService) {
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.orderRepo = orderRepo;
        this.cartService = cartService;
        this.orderService = orderService;
        this.tierConsumptionRepo = tierConsumptionRepo;
        this.policyService = policyService;
    }

    public List<Order> recentOrdersForTicket(Ticket ticket) {
        return orderRepo.findByTicketOrderByCreatedAtDesc(ticket);
    }

    @Transactional
    public CheckoutResult checkout(String eventCode, Ticket ticket, Map<Long, List<CartLine>> groupedLines) {
        if (ticket == null || !ticket.getEvent().getCode().equals(eventCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Valid ticket required.");
        }

        CheckoutResult result = new CheckoutResult();
        TierPolicy tierPolicy = policyService.tierPolicy(eventCode, ticket);
        boolean singleOrderOnly = tierPolicy.isSingleOrderOnly();
        boolean hasExistingOrder = singleOrderOnly && orderRepo.existsByTicket_Id(ticket.getId());
        boolean lockVendorAfterOrder = tierPolicy.isLockVendorAfterOrder();

        for (var entry : groupedLines.entrySet()) {
            Long vendorId = entry.getKey();
            Vendor vendor = vendorRepo.findById(vendorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
            if (!vendor.getEvent().getCode().equals(eventCode)) {
                result.rejectedByVendor.put(vendorId, "Vendor not in this event");
                continue;
            }

            if (singleOrderOnly && hasExistingOrder) {
                result.rejectedByVendor.put(vendorId, "This ticket already placed an order. Policy allows only one order total.");
                continue;
            }
            if (lockVendorAfterOrder && orderRepo.existsByTicket_IdAndVendor_Id(ticket.getId(), vendorId)) {
                result.rejectedByVendor.put(vendorId, "You already placed an order with this vendor.");
                continue;
            }

            int qtySum = entry.getValue().stream().mapToInt(CartLine::qty).sum();
            
            // Build category items list for validation
            Map<String, Integer> categoryTotals = new LinkedHashMap<>();
            for (CartLine line : entry.getValue()) {
                MenuItem menuItem = menuItemRepo.findById(line.itemId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
                String category = menuItem.getCategory() != null ? menuItem.getCategory().trim() : null;
                if (category != null && !category.isBlank()) {
                    categoryTotals.merge(category, line.qty(), Integer::sum);
                }
            }
            List<CartService.CategoryItem> categoryItems = categoryTotals.entrySet().stream()
                    .map(e -> new CartService.CategoryItem(e.getKey(), e.getValue()))
                    .toList();

            // Check category restrictions
            var policyCheck = cartService.canAddItemsWithCategories(eventCode, ticket, vendor, categoryItems);
            if (!policyCheck.allowed()) {
                result.rejectedByVendor.put(vendorId, policyCheck.message());
                continue;
            }

            List<OrderItem> orderItems = new ArrayList<>();
            String rejection = null;
            Map<MenuItem, Integer> stockUpdates = new LinkedHashMap<>();
            for (CartLine line : entry.getValue()) {
                MenuItem menuItem = menuItemRepo.findById(line.itemId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
                if (!menuItem.getVendor().getId().equals(vendorId)) {
                    rejection = "Menu item does not belong to vendor";
                    break;
                }
                if (!menuItem.isAvailable()) {
                    rejection = menuItem.getName() + " is unavailable";
                    break;
                }
                // Stock check (null = unlimited)
                Integer stock = menuItem.getStockLevel();
                if (stock != null) {
                    int remaining = stock - line.qty();
                    if (remaining < 0) {
                        int available = Math.max(0, stock);
                        rejection = "Only " + available + " of '" + menuItem.getName() + "' left in stock.";
                        break;
                    }
                    stockUpdates.put(menuItem, remaining);
                }
                OrderItem orderItem = new OrderItem();
                orderItem.setMenuItem(menuItem);
                orderItem.setQty(line.qty());
                orderItems.add(orderItem);
            }

            if (rejection != null) {
                result.rejectedByVendor.put(vendorId, rejection);
                continue;
            }

            Order order = new Order();
            order.setEvent(vendor.getEvent());
            order.setVendor(vendor);
            order.setTicket(ticket);
            order.setStatus(OrderStatus.NEW);
            
            // Calculate vendor-specific order number
            Integer maxOrderNum = orderRepo.findMaxVendorOrderNumber(vendorId);
            order.setVendorOrderNumber(maxOrderNum + 1);
            
            orderItems.forEach(order::addItem);

            orderRepo.save(order);
            // Lock to vendor immediately if one-vendor-only policy
            if (tierPolicy.hasVendorRestriction()) {
                TierConsumption tc = tierConsumptionRepo.findByEventAndTicket(vendor.getEvent(), ticket)
                        .orElseGet(() -> {
                            TierConsumption n = new TierConsumption();
                            n.setEvent(vendor.getEvent());
                            n.setTicket(ticket);
                            return n;
                        });
                if (tc.getLockedVendor() == null) {
                    tc.setLockedVendor(vendor);
                }
                tierConsumptionRepo.save(tc);
            }
            // apply stock updates
            stockUpdates.forEach((item, remaining) -> item.setStockLevel(remaining));
            if (!stockUpdates.isEmpty()) {
                menuItemRepo.saveAll(stockUpdates.keySet());
            }
            result.orders.add(order);

            if (singleOrderOnly) {
                hasExistingOrder = true;
            }
            
            // Use OrderService to broadcast the new order
            orderService.markStatus(order.getId(), OrderStatus.NEW);
        }

        return result;
    }
}
