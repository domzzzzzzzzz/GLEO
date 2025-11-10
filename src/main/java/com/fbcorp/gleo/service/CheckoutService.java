package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.Order;
import com.fbcorp.gleo.domain.OrderItem;
import com.fbcorp.gleo.domain.OrderStatus;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.OrderRepo;
import com.fbcorp.gleo.repo.VendorRepo;
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

    private final TicketService ticketService;
    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final OrderRepo orderRepo;
    private final CartService cartService;
    private final OrderService orderService;

    public CheckoutService(TicketService ticketService,
                           VendorRepo vendorRepo,
                           MenuItemRepo menuItemRepo,
                           OrderRepo orderRepo,
                           CartService cartService,
                           OrderService orderService) {
        this.ticketService = ticketService;
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.orderRepo = orderRepo;
        this.cartService = cartService;
        this.orderService = orderService;
    }

    public List<Order> recentOrdersForDevice(String eventCode, String deviceHash) {
        return ticketService.findTicketForDevice(eventCode, deviceHash)
                .map(orderRepo::findByTicketOrderByCreatedAtDesc)
                .orElseGet(List::of);
    }

    @Transactional
    public CheckoutResult checkout(String eventCode, String qr, String deviceHash, Map<Long, List<CartLine>> groupedLines) {
        Ticket ticket = ticketService.resolveTicket(eventCode, qr, deviceHash);
        CheckoutResult result = new CheckoutResult();

        for (var entry : groupedLines.entrySet()) {
            Long vendorId = entry.getKey();
            Vendor vendor = vendorRepo.findById(vendorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
            if (!vendor.getEvent().getCode().equals(eventCode)) {
                result.rejectedByVendor.put(vendorId, "Vendor not in this event");
                continue;
            }

            int qtySum = entry.getValue().stream().mapToInt(CartLine::qty).sum();
            var policyCheck = cartService.canAddToCart(eventCode, ticket, vendor, qtySum);
            if (!policyCheck.allowed()) {
                result.rejectedByVendor.put(vendorId, policyCheck.message());
                continue;
            }

            List<OrderItem> orderItems = new ArrayList<>();
            String rejection = null;
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
            result.orders.add(order);
            
            // Use OrderService to broadcast the new order
            orderService.markStatus(order.getId(), OrderStatus.NEW);
        }

        return result;
    }
}
