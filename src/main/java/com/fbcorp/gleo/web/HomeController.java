package com.fbcorp.gleo.web;

import com.fbcorp.gleo.config.TicketSessionInterceptor;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.domain.TierPolicy;
import com.fbcorp.gleo.domain.TierConsumption;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.service.CartViewService;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.TicketService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

@Controller
@RequestMapping("/e/{eventCode}")
public class HomeController {

    private final VendorRepo vendorRepo;
    private final EventPolicyService policyService;
    private final CartViewService cartViewService;
    private final TicketService ticketService;
    private final com.fbcorp.gleo.repo.MenuItemRepo menuItemRepo;
    private final com.fbcorp.gleo.repo.TierConsumptionRepo tierConsumptionRepo;
    private final com.fbcorp.gleo.repo.OrderRepo orderRepo;

    public HomeController(VendorRepo vendorRepo,
                          EventPolicyService policyService,
                          CartViewService cartViewService,
                          TicketService ticketService,
                          com.fbcorp.gleo.repo.MenuItemRepo menuItemRepo,
                          com.fbcorp.gleo.repo.TierConsumptionRepo tierConsumptionRepo,
                          com.fbcorp.gleo.repo.OrderRepo orderRepo) {
        this.vendorRepo = vendorRepo;
        this.policyService = policyService;
        this.cartViewService = cartViewService;
        this.ticketService = ticketService;
        this.menuItemRepo = menuItemRepo;
        this.tierConsumptionRepo = tierConsumptionRepo;
        this.orderRepo = orderRepo;
    }

    @GetMapping
    public String landing(@PathVariable String eventCode,
                          @RequestParam(value = "message", required = false) String message,
                          @RequestParam(value = "next", required = false) String next,
                          Model model,
                          HttpSession session){
        var event = policyService.get(eventCode);
        model.addAttribute("event", event);
        var vendors = vendorRepo.findByEventAndActiveTrue(event);
        model.addAttribute("vendors", vendors);
        model.addAttribute("vendorHeroMap", buildVendorHeroMap(vendors));
        Set<Long> exhaustedVendors = new HashSet<>();
        
        var cartSummary = cartViewService.summarize(event, getOrCreateCart(session));
        model.addAttribute("cartSummary", cartSummary);
        
        // Derived flags for vendor locking
        boolean hasCartGroups = cartSummary != null && cartSummary.groups() != null && !cartSummary.groups().isEmpty();
        Long lockedVendorId = hasCartGroups ? cartSummary.groups().get(0).vendorId() : null;
        model.addAttribute("lockedVendorId", lockedVendorId);
        
        var activeTicket = resolveActiveTicket(eventCode, session);
        boolean hasTicket = activeTicket.isPresent();
        TierCode tierCode = activeTicket.map(Ticket::getEffectiveTierCode).orElse(null);

        boolean oneVendorOnlyPolicy = activeTicket.isPresent() && policyService.hasOneVendorOnlyPolicy(eventCode, activeTicket.get());
        model.addAttribute("oneVendorOnlyPolicy", oneVendorOnlyPolicy);

        if (hasTicket) {
            tierConsumptionRepo.findByEventAndTicket(event, activeTicket.get())
                    .map(tc -> tc.getLockedVendor() != null ? tc.getLockedVendor().getId() : null)
                    .ifPresent(id -> model.addAttribute("lockedVendorId", id));

            TierPolicy tp = policyService.tierPolicy(eventCode, activeTicket.get());
            if (tp != null && tp.hasLimit()) {
                tierConsumptionRepo.findByEventAndTicket(event, activeTicket.get()).ifPresent(tc -> {
                    int limit = Math.max(0, tp.getMaxItemsPerVendor());
                    for (var v : vendors) {
                        int consumed = tc.getVendorConsumption(v.getId());
                        if (consumed >= limit) {
                            exhaustedVendors.add(v.getId());
                        }
                    }
                });
            }

            if (tp != null) {
                // If policy allows only ONE order total and an order already exists, lock all vendors
                if (tp.isSingleOrderOnly() && orderRepo.existsByTicket_Id(activeTicket.get().getId())) {
                    vendors.forEach(v -> exhaustedVendors.add(v.getId()));
                }
                // If policy locks a vendor after first order, mark that vendor as exhausted
                if (tp.isLockVendorAfterOrder()) {
                    for (var v : vendors) {
                        if (orderRepo.existsByTicket_IdAndVendor_Id(activeTicket.get().getId(), v.getId())) {
                            exhaustedVendors.add(v.getId());
                        }
                    }
                }
            }
        }

        model.addAttribute("exhaustedVendors", exhaustedVendors);
        model.addAttribute("needsTicket", !hasTicket);
        model.addAttribute("ticketPostUrl", "/e/" + eventCode + "/ticket");
        String sanitizedNext = sanitizeNext(eventCode, next);
        boolean hasCustomNext = StringUtils.hasText(next) && !("/e/" + eventCode).equals(sanitizedNext);
        if (hasCustomNext) {
            model.addAttribute("ticketNext", sanitizedNext);
        }
        if (StringUtils.hasText(message)) {
            model.addAttribute("ticketOverlayMessage", message);
        }

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

    private String sanitizeNext(String eventCode, String next) {
        if (StringUtils.hasText(next) && next.startsWith("/e/" + eventCode)) {
            return next;
        }
        return "/e/" + eventCode;
    }

    private java.util.Map<Long, String> buildVendorHeroMap(java.util.List<com.fbcorp.gleo.domain.Vendor> vendors) {
        java.util.Map<Long, String> heroMap = new java.util.HashMap<>();
        for (var v : vendors) {
            String hero = v.getHeroImagePath();
            if (!StringUtils.hasText(hero)) {
                if (StringUtils.hasText(v.getImagePath())) {
                    hero = v.getImagePath();
                }
            }
            if (!StringUtils.hasText(hero)) {
                var menuHero = menuItemRepo.findFirstByVendorAndImagePathIsNotNullOrderByItemOrderAscIdAsc(v);
                if (menuHero != null && StringUtils.hasText(menuHero.getImagePath())) {
                    hero = menuHero.getImagePath();
                }
            }
            heroMap.put(v.getId(), StringUtils.hasText(hero) ? hero : null);
        }
        return heroMap;
    }
}
