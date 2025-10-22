package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Order;
import com.fbcorp.gleo.domain.OrderStatus;
import com.fbcorp.gleo.repo.OrderRepo;
import com.fbcorp.gleo.domain.UserAccount;
import com.fbcorp.gleo.repo.UserAccountRepo;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.OrderService;
import com.fbcorp.gleo.service.VendorAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/e/{eventCode}/usher")
public class UsherController {

    private static final Map<OrderStatus, String> STATUS_LABELS;
    private static final Map<OrderStatus, String> ACTION_LABELS;

    static {
        Map<OrderStatus, String> labels = new LinkedHashMap<>();
        labels.put(OrderStatus.NEW, "Checked-in");
        labels.put(OrderStatus.PREPARING, "Being prepared");
        labels.put(OrderStatus.READY, "Ready for pickup");
        labels.put(OrderStatus.COMPLETED, "Completed");
        labels.put(OrderStatus.CANCELLED, "Cancelled");
        STATUS_LABELS = Collections.unmodifiableMap(labels);

        EnumMap<OrderStatus, String> actions = new EnumMap<>(OrderStatus.class);
        actions.put(OrderStatus.NEW, "Mark preparing");
        actions.put(OrderStatus.PREPARING, "Mark ready");
        actions.put(OrderStatus.READY, "Complete order");
        ACTION_LABELS = Collections.unmodifiableMap(actions);
    }

    private final EventPolicyService policyService;
    private final OrderRepo orderRepo;
    private final OrderService orderService;
    private final VendorAuthService vendorAuthService;
    private final UserAccountRepo userAccountRepo;

    public UsherController(EventPolicyService policyService,
                           OrderRepo orderRepo,
                           OrderService orderService,
                           VendorAuthService vendorAuthService,
                           UserAccountRepo userAccountRepo) {
        this.policyService = policyService;
        this.orderRepo = orderRepo;
        this.orderService = orderService;
        this.vendorAuthService = vendorAuthService;
        this.userAccountRepo = userAccountRepo;
    }

    public record StatusBucket(OrderStatus status, String label, List<Order> orders) {
        public boolean hasOrders() {
            return orders != null && !orders.isEmpty();
        }

        public OrderStatus getStatus() {
            return status;
        }

        public String getLabel() {
            return label;
        }

        public List<Order> getOrders() {
            return orders;
        }
    }

    @GetMapping
    public String board(@PathVariable String eventCode,
                        @RequestParam(name = "ticket", required = false) String ticketFilter,
                        Model model) {
        buildBoardModel(eventCode, ticketFilter, model);
        return "usher_board";
    }

    @GetMapping("/live")
    public String boardLive(@PathVariable String eventCode,
                            @RequestParam(name = "ticket", required = false) String ticketFilter,
                            Model model) {
        buildBoardModel(eventCode, ticketFilter, model);
        return "usher_board :: board";
    }

    private void buildBoardModel(String eventCode,
                                 String ticketFilter,
                                 Model model) {
        var event = policyService.get(eventCode);
        var account = currentAccount();
        List<Order> baseOrders;
        if (account != null && account.hasRole("ROLE_USHER")) {
            var vendor = requireAuthorizedVendor(account, eventCode);
            baseOrders = new ArrayList<>(orderRepo.findByVendor(vendor));
            model.addAttribute("activeVendor", vendor);
        } else {
            baseOrders = new ArrayList<>(orderRepo.findByEvent(event));
        }

        String normalizedFilter = ticketFilter != null ? ticketFilter.trim() : "";
        List<Order> filteredOrders = baseOrders;
        if (!normalizedFilter.isBlank()) {
            String token = normalizedFilter.toLowerCase(Locale.ROOT);
            filteredOrders = baseOrders.stream()
                    .filter(order -> matchesTicket(order, token))
                    .collect(Collectors.toList());
        }

        Comparator<Order> byCreatedAt = Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        final List<Order> ordersForBuckets = filteredOrders;
        List<StatusBucket> buckets = STATUS_LABELS.entrySet().stream()
                .map(entry -> {
                    List<Order> bucketOrders = ordersForBuckets.stream()
                            .filter(o -> o.getStatus() == entry.getKey())
                            .sorted(byCreatedAt)
                            .collect(Collectors.toList());
                    return new StatusBucket(entry.getKey(), entry.getValue(), bucketOrders);
                })
                .filter(bucket -> bucket.status() != OrderStatus.CANCELLED || bucket.hasOrders())
                .collect(Collectors.toList());

        model.addAttribute("event", event);
        model.addAttribute("statusBuckets", buckets);
        model.addAttribute("statusActionLabels", ACTION_LABELS);
        model.addAttribute("requirePin", policyService.requirePin(eventCode));
        model.addAttribute("guestConfirmEnabled", policyService.guestPickupEnabled(eventCode));
        model.addAttribute("ticketFilter", normalizedFilter);
        model.addAttribute("hasAnyOrders", buckets.stream().anyMatch(StatusBucket::hasOrders));
    }

    @PostMapping("/orders/{orderId}/advance")
    public String advance(@PathVariable String eventCode,
                          @PathVariable Long orderId,
                          @RequestParam(name = "pin", required = false) String vendorPin,
                          RedirectAttributes redirectAttributes) {
        var event = policyService.get(eventCode);
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.getEvent().getId().equals(event.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        enforceOrderAccess(eventCode, order);

        OrderStatus current = order.getStatus();
        try {
            switch (current) {
                case NEW -> {
                    orderService.markStatus(orderId, OrderStatus.PREPARING);
                    redirectAttributes.addFlashAttribute("message", "Order #" + orderId + " marked as preparing.");
                }
                case PREPARING -> {
                    orderService.markStatus(orderId, OrderStatus.READY);
                    redirectAttributes.addFlashAttribute("message", "Order #" + orderId + " marked as ready.");
                }
                case READY -> {
                    boolean requirePin = policyService.requirePin(eventCode);
                    if (requirePin && (vendorPin == null || vendorPin.isBlank())) {
                        redirectAttributes.addFlashAttribute("error", "Vendor PIN required to complete order #" + orderId + ".");
                        return "redirect:/e/" + eventCode + "/usher";
                    }
                    if (requirePin && !vendorAuthService.isValidPinForOrder(orderId, vendorPin)) {
                        redirectAttributes.addFlashAttribute("error", "PIN is invalid for order #" + orderId + ".");
                        return "redirect:/e/" + eventCode + "/usher";
                    }
                    orderService.markCompletedByGuest(orderId, "usher-board", requirePin ? vendorPin : null);
                    redirectAttributes.addFlashAttribute("message", "Order #" + orderId + " completed.");
                }
                default -> redirectAttributes.addFlashAttribute("message", "Order #" + orderId + " already finalized.");
            }
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getReason() != null ? ex.getReason() : "Unable to update order.");
        }

        return "redirect:/e/" + eventCode + "/usher";
    }

    @PostMapping("/orders/{orderId}/cancel")
    public String cancel(@PathVariable String eventCode,
                         @PathVariable Long orderId,
                         RedirectAttributes redirectAttributes) {
        var event = policyService.get(eventCode);
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.getEvent().getId().equals(event.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        enforceOrderAccess(eventCode, order);

        orderService.markStatus(orderId, OrderStatus.CANCELLED);
        redirectAttributes.addFlashAttribute("message", "Order #" + orderId + " cancelled.");
        return "redirect:/e/" + eventCode + "/usher";
    }

    private boolean matchesTicket(Order order, String token) {
        if (order.getTicket() == null) {
            return false;
        }
        var ticket = order.getTicket();
        return containsIgnoreCase(ticket.getQrCode(), token)
                || containsIgnoreCase(ticket.getSerial(), token)
                || containsIgnoreCase(ticket.getHolderName(), token);
    }

    private boolean containsIgnoreCase(String value, String token) {
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(token);
    }

    private UserAccount currentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return userAccountRepo.findByUsername(authentication.getName()).orElse(null);
    }

    private com.fbcorp.gleo.domain.Vendor requireAuthorizedVendor(UserAccount account, String eventCode) {
        var vendor = account.getVendor();
        if (vendor == null || vendor.getEvent() == null || !vendor.getEvent().getCode().equals(eventCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this event.");
        }
        return vendor;
    }

    private void enforceOrderAccess(String eventCode, Order order) {
        var account = currentAccount();
        if (account != null && account.hasRole("ROLE_USHER")) {
            var vendor = requireAuthorizedVendor(account, eventCode);
            if (order.getVendor() == null || !order.getVendor().getId().equals(vendor.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to manage this order.");
            }
        }
    }
}
