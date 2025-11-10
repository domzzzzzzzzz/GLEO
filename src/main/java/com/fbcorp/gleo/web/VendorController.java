package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.service.CartViewService;
import com.fbcorp.gleo.service.EventPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/e/{eventCode}/v")
public class VendorController {
    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final EventPolicyService policyService;
    private final CartViewService cartViewService;

    public VendorController(VendorRepo vendorRepo, MenuItemRepo menuItemRepo, EventPolicyService policyService, CartViewService cartViewService){
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.policyService = policyService;
        this.cartViewService = cartViewService;
    }

    @GetMapping("/{vendorId}")
    public String menu(@PathVariable String eventCode, @PathVariable Long vendorId, Model model, HttpSession session){
        var event = policyService.get(eventCode);
        Vendor v = vendorRepo.findById(vendorId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!v.getEvent().getId().equals(event.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("event", event);
        model.addAttribute("vendor", v);
        model.addAttribute("items", menuItemRepo.findByVendorAndAvailableTrue(v));
        model.addAttribute("multiVendorEnabled", policyService.multiVendorCart(eventCode));
        var cartSummary = cartViewService.summarize(getOrCreateCart(session));
        model.addAttribute("cartSummary", cartSummary);
        // Derived, safe flags for template (avoid indexing into groups):
        boolean hasCartGroups = cartSummary != null && cartSummary.groups() != null && !cartSummary.groups().isEmpty();
        model.addAttribute("cartHasVendor", hasCartGroups);
        Long lockedVendorId = hasCartGroups ? cartSummary.groups().get(0).vendorId() : null;
        model.addAttribute("lockedVendorId", lockedVendorId);
        model.addAttribute("lockedVendorName", hasCartGroups ? cartSummary.groups().get(0).vendorName() : null);
    // Lock only when multi-vendor is DISABLED and cart already has a different vendor
    boolean singleVendorLocked = !policyService.multiVendorCart(eventCode) && hasCartGroups && !lockedVendorId.equals(v.getId());
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
}
