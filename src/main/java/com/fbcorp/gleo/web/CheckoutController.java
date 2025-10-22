package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Order;
import com.fbcorp.gleo.service.CheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/e/{eventCode}")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService){
        this.checkoutService = checkoutService;
    }

    private String deviceHash(HttpServletRequest req){
        // Simple demo fingerprint
        String ua = req.getHeader("User-Agent");
        String ip = req.getRemoteAddr();
        return Integer.toHexString(Objects.hash(ua, ip));
    }

    @GetMapping("/checkout")
    public String checkoutSummary(@PathVariable String eventCode,
                                  HttpServletRequest request,
                                  Model model){
        var orders = checkoutService.recentOrdersForDevice(eventCode, deviceHash(request));
        populateSummary(model, eventCode, orders, Collections.emptyMap());
        return "checkout_result";
    }

    @PostMapping("/checkout")
    public String checkout(@PathVariable String eventCode,
                           @RequestParam(name = "qr", required = false) String qr,
                           HttpServletRequest request,
                           HttpSession session,
                           Model model){
        CartSession cart = (CartSession) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()){
            return "redirect:/e/" + eventCode + "/cart";
        }

        Map<Long, List<CheckoutService.CartLine>> groups = new LinkedHashMap<>();
        cart.getAll().forEach((vendorId, itemsMap) -> {
            List<CheckoutService.CartLine> lines = new ArrayList<>();
            itemsMap.forEach((itemId, qty) -> lines.add(new CheckoutService.CartLine(itemId, qty)));
            groups.put(vendorId, lines);
        });

        var normalizedQr = (qr != null && !qr.isBlank()) ? qr.trim() : null;
        var result = checkoutService.checkout(eventCode, normalizedQr, deviceHash(request), groups);
        // remove accepted groups from cart
        result.orders.forEach(o -> cart.removeVendorGroup(o.getVendor().getId()));

        populateSummary(model, eventCode, result.orders, result.rejectedByVendor);
        return "checkout_result";
    }

    private void populateSummary(Model model,
                                 String eventCode,
                                 List<Order> orders,
                                 Map<Long, String> rejected){
        model.addAttribute("eventCode", eventCode);
        model.addAttribute("orders", orders);
        model.addAttribute("rejected", rejected);
        model.addAttribute("createdCount", orders.size());
        model.addAttribute("rejectedCount", rejected.size());
    }
}
