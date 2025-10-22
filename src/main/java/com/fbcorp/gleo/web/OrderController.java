package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.OrderStatus;
import com.fbcorp.gleo.repo.OrderRepo;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.OrderService;
import com.fbcorp.gleo.service.VendorAuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/e/{eventCode}/orders")
public class OrderController {
    private final OrderRepo orderRepo;
    private final OrderService orderService;
    private final VendorAuthService vendorAuthService;
    private final EventPolicyService policyService;

    public OrderController(OrderRepo orderRepo, OrderService orderService,
                           VendorAuthService vendorAuthService, EventPolicyService policyService){
        this.orderRepo = orderRepo;
        this.orderService = orderService;
        this.vendorAuthService = vendorAuthService;
        this.policyService = policyService;
    }

    @GetMapping("/{orderId}")
    public String track(@PathVariable String eventCode, @PathVariable Long orderId, Model model){
        var event = policyService.get(eventCode);
        var order = orderRepo.findById(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.getEvent().getId().equals(event.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("event", event);
        model.addAttribute("order", order);
        model.addAttribute("requirePin", policyService.requirePin(eventCode));
        return "order_track";
    }

    @PostMapping("/{orderId}/confirm-pickup")
    public String confirm(@PathVariable String eventCode, @PathVariable Long orderId,
                          @RequestParam(required=false) String pin,
                          HttpSession session, Model model){
        if (!policyService.guestPickupEnabled(eventCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pickup confirm disabled");
        }
        boolean requirePin = policyService.requirePin(eventCode);
        if (requirePin && (pin == null || !vendorAuthService.isValidPinForOrder(orderId, pin))){
            model.addAttribute("message", "Invalid PIN");
            return "fragments/toast :: error";
        }
        orderService.markCompletedByGuest(orderId, "device", requirePin ? pin : null);
        var order = orderRepo.findById(orderId).orElseThrow();
        model.addAttribute("order", order);
        return "fragments/order_status :: badge";
    }

    @PostMapping("/{orderId}/status")
    public String changeStatus(@PathVariable String eventCode, @PathVariable Long orderId,
                               @RequestParam OrderStatus status, Model model){
        orderService.markStatus(orderId, status);
        var order = orderRepo.findById(orderId).orElseThrow();
        model.addAttribute("order", order);
        return "fragments/order_status :: badge";
    }
}
