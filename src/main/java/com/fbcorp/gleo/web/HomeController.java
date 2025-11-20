package com.fbcorp.gleo.web;

import com.fbcorp.gleo.config.TicketSessionInterceptor;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.service.CartViewService;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.TicketService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpSession;

import java.util.Optional;

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
                          HttpSession session){
        var event = policyService.get(eventCode);
        model.addAttribute("event", event);
        model.addAttribute("vendors", vendorRepo.findByEventAndActiveTrue(event));
        
        var cartSummary = cartViewService.summarize(event, getOrCreateCart(session));
        model.addAttribute("cartSummary", cartSummary);
        
        // Derived flags for vendor locking
        boolean hasCartGroups = cartSummary != null && cartSummary.groups() != null && !cartSummary.groups().isEmpty();
        Long lockedVendorId = hasCartGroups ? cartSummary.groups().get(0).vendorId() : null;
        model.addAttribute("lockedVendorId", lockedVendorId);
        
        var activeTicket = resolveActiveTicket(eventCode, session);
        boolean hasTicket = activeTicket.isPresent();
        TierCode tierCode = activeTicket.map(Ticket::getTierCode).orElse(null);

        boolean oneVendorOnlyPolicy = tierCode != null && policyService.hasOneVendorOnlyPolicy(eventCode, tierCode);
        model.addAttribute("oneVendorOnlyPolicy", oneVendorOnlyPolicy);

        model.addAttribute("needsTicket", !hasTicket);
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

    private Optional<Ticket> resolveActiveTicket(String eventCode, HttpSession session) {
        Object attr = session.getAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR);
        if (attr instanceof Long ticketId) {
            var ticketOpt = ticketService.findTicketByIdAndEvent(ticketId, eventCode);
            if (ticketOpt.isPresent()) {
                return ticketOpt;
            }
            session.removeAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR);
        }
        return java.util.Optional.empty();
    }
}
