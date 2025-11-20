package com.fbcorp.gleo.web;

import com.fbcorp.gleo.config.TicketSessionInterceptor;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.repo.TicketRepo;
import com.fbcorp.gleo.service.CartViewService;
import com.fbcorp.gleo.service.EventPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import java.util.Comparator;

@Controller
@RequestMapping("/e/{eventCode}/v")
public class VendorController {
    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final EventPolicyService policyService;
    private final CartViewService cartViewService;
    private final TicketRepo ticketRepo;

    public VendorController(VendorRepo vendorRepo, MenuItemRepo menuItemRepo, EventPolicyService policyService, CartViewService cartViewService, TicketRepo ticketRepo){
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.policyService = policyService;
        this.cartViewService = cartViewService;
        this.ticketRepo = ticketRepo;
    }

    @GetMapping("/{vendorId}")
    public String menu(@PathVariable String eventCode, @PathVariable Long vendorId, Model model, HttpSession session){
        var event = policyService.get(eventCode);
        Vendor v = vendorRepo.findById(vendorId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!v.getEvent().getId().equals(event.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        
        Ticket ticket = resolveActiveTicket(eventCode, session);
        if (ticket == null) {
            return "redirect:/e/" + eventCode + "/ticket?next=/e/" + eventCode + "/v/" + vendorId;
        }
        
        // Add ticket info to model for display/policy enforcement
        model.addAttribute("ticket", ticket);
        model.addAttribute("tierCode", ticket.getTierCode());
        
        model.addAttribute("event", event);
        model.addAttribute("vendor", v);
    var items = menuItemRepo.findByVendorAndAvailableTrue(v);
    items.sort(Comparator
        .comparing((com.fbcorp.gleo.domain.MenuItem i) -> i.getCategoryOrder() == null ? Integer.MAX_VALUE : i.getCategoryOrder())
        .thenComparing(i -> i.getItemOrder() == null ? Integer.MAX_VALUE : i.getItemOrder())
        .thenComparing(i -> i.getName() == null ? "" : i.getName(), String.CASE_INSENSITIVE_ORDER));
    model.addAttribute("items", items);
    // group items by category for display; use "Uncategorized" for blanks
    java.util.Map<String, java.util.List<com.fbcorp.gleo.domain.MenuItem>> grouped = new java.util.LinkedHashMap<>();
    for (com.fbcorp.gleo.domain.MenuItem i : items) {
        String key = (i.getCategory() == null || i.getCategory().isBlank()) ? "Uncategorized" : i.getCategory();
        grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(i);
    }
    // Determine ordering for categories: use the minimum categoryOrder among items in the category.
    java.util.List<java.util.Map.Entry<String, java.util.List<com.fbcorp.gleo.domain.MenuItem>>> entries = new java.util.ArrayList<>(grouped.entrySet());
    entries.sort((e1, e2) -> {
        int o1 = e1.getValue().stream().map(com.fbcorp.gleo.domain.MenuItem::getCategoryOrder).filter(java.util.Objects::nonNull).min(Integer::compareTo).orElse(0);
        int o2 = e2.getValue().stream().map(com.fbcorp.gleo.domain.MenuItem::getCategoryOrder).filter(java.util.Objects::nonNull).min(Integer::compareTo).orElse(0);
        if (o1 != o2) return Integer.compare(o1, o2);
        return e1.getKey().compareToIgnoreCase(e2.getKey());
    });
    java.util.LinkedHashMap<String, java.util.List<com.fbcorp.gleo.domain.MenuItem>> ordered = new java.util.LinkedHashMap<>();
    for (var e : entries) {
        // Sort items inside a category alphabetically by name for deterministic order
        e.getValue().sort(Comparator
            .comparing((com.fbcorp.gleo.domain.MenuItem i) -> i.getItemOrder() == null ? Integer.MAX_VALUE : i.getItemOrder())
            .thenComparing(i -> i.getName() == null ? "" : i.getName()));
        ordered.put(e.getKey(), e.getValue());
    }
    model.addAttribute("itemsByCategory", ordered);
        model.addAttribute("multiVendorEnabled", policyService.multiVendorCart(eventCode));
        var cartSummary = cartViewService.summarize(event, getOrCreateCart(session));
        model.addAttribute("cartSummary", cartSummary);
        // Derived, safe flags for template (avoid indexing into groups):
        boolean hasCartGroups = cartSummary != null && cartSummary.groups() != null && !cartSummary.groups().isEmpty();
        model.addAttribute("cartHasVendor", hasCartGroups);
        Long lockedVendorId = hasCartGroups ? cartSummary.groups().get(0).vendorId() : null;
        model.addAttribute("lockedVendorId", lockedVendorId);
        model.addAttribute("lockedVendorName", hasCartGroups ? cartSummary.groups().get(0).vendorName() : null);
        
        // Check if tier policy has "one vendor only" restriction
        boolean oneVendorOnlyPolicy = policyService.hasOneVendorOnlyPolicy(eventCode, ticket.getTierCode());
        model.addAttribute("oneVendorOnlyPolicy", oneVendorOnlyPolicy);
        
        // Lock only when multi-vendor is DISABLED and cart already has a different vendor
        // OR when one vendor only policy is active and cart has a different vendor
        boolean singleVendorLocked = (!policyService.multiVendorCart(eventCode) || oneVendorOnlyPolicy) 
                                     && hasCartGroups && !lockedVendorId.equals(v.getId());
        model.addAttribute("singleVendorLocked", singleVendorLocked);
        model.addAttribute("cartLineCount", hasCartGroups ? cartSummary.totalQty() : 0);
        return "vendor_menu";
    }

    private CartSession getOrCreateCart(HttpSession session) {
        CartSession cart = (CartSession) session.getAttribute("CART");
        if (cart == null) {
            cart = new CartSession();
            session.setAttribute("CART", cart);
        }
        return cart;
    }

    private Ticket resolveActiveTicket(String eventCode, HttpSession session) {
        Object attr = session.getAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR);
        if (attr instanceof Long ticketId) {
            return ticketRepo.findById(ticketId)
                    .filter(t -> t.isActive() && t.getEvent().getCode().equals(eventCode))
                    .orElseGet(() -> {
                        session.removeAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR);
                        return null;
                    });
        }
        return null;
    }
}
