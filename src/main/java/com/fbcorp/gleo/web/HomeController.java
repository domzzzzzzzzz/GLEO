package com.fbcorp.gleo.web;

import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.service.CartViewService;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.TicketService;
import com.fbcorp.gleo.web.util.DeviceFingerprint;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/e/{eventCode}")
public class HomeController {

    private final VendorRepo vendorRepo;
    private final EventPolicyService policyService;
    private final CartViewService cartViewService;
    private final TicketService ticketService;

    public HomeController(VendorRepo vendorRepo,
                          EventPolicyService policyService,
                          CartViewService cartViewService,
                          TicketService ticketService) {
        this.vendorRepo = vendorRepo;
        this.policyService = policyService;
        this.cartViewService = cartViewService;
        this.ticketService = ticketService;
    }

    @GetMapping
    public String landing(@PathVariable String eventCode,
                          Model model,
                          HttpSession session,
                          HttpServletRequest request,
                          Authentication authentication){
        var event = policyService.get(eventCode);
        model.addAttribute("event", event);
        model.addAttribute("vendors", vendorRepo.findByEventAndActiveTrue(event));
        
        var cartSummary = cartViewService.summarize(getOrCreateCart(session));
        model.addAttribute("cartSummary", cartSummary);
        
        // Derived flags for vendor locking
        boolean hasCartGroups = cartSummary != null && cartSummary.groups() != null && !cartSummary.groups().isEmpty();
        Long lockedVendorId = hasCartGroups ? cartSummary.groups().get(0).vendorId() : null;
        model.addAttribute("lockedVendorId", lockedVendorId);
        
        // Check if user is authenticated with a ticket (not admin/organizer/vendor)
        boolean hasTicketAuth = false;
        com.fbcorp.gleo.domain.TierCode tierCode = null;
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            // Check if it's NOT anonymousUser and NOT an admin/organizer/vendor account
            hasTicketAuth = username != null 
                && !username.equals("anonymousUser")
                && !username.equals("admin")
                && !username.startsWith("organizer_")
                && !username.startsWith("vendor_")
                && !username.startsWith("usher_");
            
            // Get tier code from ticket
            if (hasTicketAuth) {
                var ticket = ticketService.getTicketByQrCode(username);
                if (ticket.isPresent()) {
                    tierCode = ticket.get().getTierCode();
                }
            }
        }
        
        // Check for one vendor only policy
        boolean oneVendorOnlyPolicy = false;
        if (tierCode != null) {
            oneVendorOnlyPolicy = policyService.hasOneVendorOnlyPolicy(eventCode, tierCode);
        }
        model.addAttribute("oneVendorOnlyPolicy", oneVendorOnlyPolicy);
        
        model.addAttribute("needsTicket", !hasTicketAuth);
        model.addAttribute("ticketPostUrl", "/e/" + eventCode + "/ticket");
        return "index";
    }

    private CartSession getOrCreateCart(HttpSession session) {
        CartSession cart = (CartSession) session.getAttribute("CART");
        if (cart == null) {
            cart = new CartSession();
            session.setAttribute("CART", cart);
        }
        return cart;
    }
}

