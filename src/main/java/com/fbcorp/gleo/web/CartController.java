package com.fbcorp.gleo.web;

import com.fbcorp.gleo.config.TicketSessionInterceptor;
import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.TicketRepo;
import com.fbcorp.gleo.repo.PromoCodeRepo;
import com.fbcorp.gleo.service.CartViewService;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

@Controller
@RequestMapping("/e/{eventCode}/cart")
public class CartController {

    private static final String CART_FRAGMENT = "fragments/cart_panel :: panel";

    private final MenuItemRepo menuItemRepo;
    private final TicketRepo ticketRepo;
    private final EventPolicyService policyService;
    private final CartViewService cartViewService;
    private final CartService cartService;
    private final PromoCodeRepo promoCodeRepo;

    public CartController(MenuItemRepo menuItemRepo,
                          TicketRepo ticketRepo,
                          EventPolicyService policyService,
                          CartViewService cartViewService,
                          CartService cartService,
                          PromoCodeRepo promoCodeRepo){
        this.menuItemRepo = menuItemRepo;
        this.ticketRepo = ticketRepo;
        this.policyService = policyService;
        this.cartViewService = cartViewService;
        this.cartService = cartService;
        this.promoCodeRepo = promoCodeRepo;
    }

    private CartSession cart(HttpSession session){
        CartSession c = (CartSession) session.getAttribute("CART");
        if (c == null){
            c = new CartSession();
            session.setAttribute("CART", c);
        }
        return c;
    }

    private boolean isHx(HttpServletRequest request){
        return request.getHeader("HX-Request") != null;
    }

    private void populateCartModel(String eventCode, CartSession cartSession, Model model){
        var event = policyService.get(eventCode);
        populateCartModel(event, cartSession, model);
    }

    private void populateCartModel(Event event, CartSession cartSession, Model model){
        model.addAttribute("event", event);
        if (event == null) {
            model.addAttribute("cartSummary", CartViewService.CartSummary.empty());
            model.addAttribute("isMultiVendor", false);
            return;
        }
        model.addAttribute("isMultiVendor", policyService.multiVendorCart(event.getCode()));
        model.addAttribute("cartSummary", cartViewService.summarize(event, cartSession));
    }

    // --- New endpoints for quantity, notes, and promo code updates ---

    @PostMapping("/set-qty")
    public String setQty(@PathVariable String eventCode,
                         @RequestParam Long vendorId,
                         @RequestParam Long itemId,
                         @RequestParam int qty,
                         HttpSession session,
                         HttpServletRequest request,
                         Model model){
        CartSession cart = cart(session);
        
        if (qty <= 0){
            cart.removeItem(vendorId, itemId);
            populateCartModel(eventCode, cart, model);
        } else {
            // Get the item to check category limits
            MenuItem item = menuItemRepo.findById(itemId).orElse(null);
            if (item == null) {
                populateCartModel(eventCode, cart, model);
                model.addAttribute("errorMessage", "Item not found.");
                if (isHx(request)) {
                    return "cart";
                }
                return "redirect:/e/" + eventCode + "/cart";
            }
            
            Vendor vendor = item.getVendor();
            Ticket ticket = activeTicket(eventCode, session);

            if (ticket != null && ticket.getEvent().getCode().equals(eventCode)) {
                CartService.CheckResult vendorCheck = cartService.canAddToCart(eventCode, ticket, vendor, qty);
                if (!vendorCheck.allowed()) {
                    populateCartModel(eventCode, cart, model);
                    model.addAttribute("errorMessage", vendorCheck.message());
                    return isHx(request) ? "cart" : "redirect:/e/" + eventCode + "/cart";
                }
            }
            
            // Check category limits before updating quantity
            if (ticket != null && ticket.getEvent().getCode().equals(eventCode)) {
                Map<Long, Integer> vendorCart = cart.getAll().get(vendor.getId());
                Map<String, Integer> categoryTotals = new HashMap<>();
                
                // Build category totals with NEW quantity
                if (vendorCart != null) {
                    for (Map.Entry<Long, Integer> entry : vendorCart.entrySet()) {
                    MenuItem cartItem = menuItemRepo.findById(entry.getKey()).orElse(null);
                    if (cartItem != null) {
                        String category = cartItem.getCategory() != null ? cartItem.getCategory().trim() : "";
                        if (!category.isBlank()) {
                            int itemQty = entry.getKey().equals(itemId) ? qty : entry.getValue();
                            categoryTotals.merge(category, itemQty, Integer::sum);
                        }
                    }
                    }
                }
                
                // Check if new quantity is allowed
                List<CartService.CategoryItem> itemsToCheck = categoryTotals.entrySet().stream()
                    .map(e -> new CartService.CategoryItem(e.getKey(), e.getValue()))
                    .toList();
                
                CartService.CheckResult check = cartService.canAddItemsWithCategories(
                    eventCode, ticket, vendor, itemsToCheck
                );
                
                if (!check.allowed()) {
                    // DON'T UPDATE QUANTITY - just show error and return current cart
                    populateCartModel(eventCode, cart, model);
                    model.addAttribute("errorMessage", check.message());
                    if (isHx(request)) {
                        return "cart";
                    }
                    return "redirect:/e/" + eventCode + "/cart";
                }
            }
            
            // If validation passed, update quantity
            cart.setQty(vendorId, itemId, Math.min(qty, 99));
            populateCartModel(eventCode, cart, model);
        }
        
        // For HTMX from cart page, return full cart page
        if (isHx(request)) {
            return "cart";
        }
        return "redirect:/e/" + eventCode + "/cart";
    }

    @PostMapping("/note")
    public String setVendorNote(@PathVariable String eventCode,
                                @RequestParam Long vendorId,
                                @RequestParam(required=false) String note,
                                HttpSession session,
                                HttpServletRequest request,
                                Model model){
        CartSession cart = cart(session);
        cart.setVendorNote(vendorId, note);
        populateCartModel(eventCode, cart, model);
        model.addAttribute("successMessage", "Note updated.");
        return isHx(request) ? CART_FRAGMENT : "redirect:/e/" + eventCode + "/cart";
    }

    @PostMapping("/promo")
    public String applyPromo(@PathVariable String eventCode,
                             @RequestParam String code,
                             HttpSession session,
                             HttpServletRequest request,
                             Model model){
        CartSession cart = cart(session);
        Event event = policyService.get(eventCode);

        String sanitized = code == null ? "" : code.trim();
        if (sanitized.isEmpty()) {
            cart.setPromoCode(null);
            populateCartModel(event, cart, model);
            model.addAttribute("successMessage", "Promo removed.");
            return isHx(request) ? CART_FRAGMENT : "redirect:/e/" + eventCode + "/cart";
        }

        var promo = promoCodeRepo.findByEventAndCodeIgnoreCaseAndActiveTrue(event, sanitized);
        if (promo.isPresent()) {
            cart.setPromoCode(promo.get().getCode());
            populateCartModel(event, cart, model);
            model.addAttribute("successMessage", "Promo " + promo.get().getCode() + " applied.");
        } else {
            cart.setPromoCode(null);
            populateCartModel(event, cart, model);
            model.addAttribute("errorMessage", "Promo code not found or inactive.");
        }
        return isHx(request) ? CART_FRAGMENT : "redirect:/e/" + eventCode + "/cart";
    }

    @PostMapping("/add")
    public String add(@PathVariable String eventCode,
                      @RequestParam Long itemId,
                      @RequestParam(defaultValue = "1") int qty,
                      HttpSession session,
                      HttpServletRequest request,
                      Model model,
                      RedirectAttributes redirectAttributes){
        MenuItem item = menuItemRepo.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Vendor vendor = item.getVendor();
        if (!vendor.getEvent().getCode().equals(eventCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        CartSession cart = cart(session);
        
        // Multi-vendor policy check
        if (!policyService.multiVendorCart(eventCode)) {
            Map<Long, Map<Long, Integer>> lines = cart.getAll();
            if (!lines.isEmpty() && !lines.containsKey(vendor.getId())) {
                String errorMessage = "🏪 This event allows orders from one vendor at a time. Please complete or clear your current cart first.";
                if (isHx(request)) {
                    populateCartModel(eventCode, cart, model);
                    model.addAttribute("errorMessage", errorMessage);
                    return CART_FRAGMENT;
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/e/" + eventCode + "/v/" + vendor.getId();
                }
            }
        }

        // Category restrictions check - works for both authenticated and guest users
        Ticket ticket = activeTicket(eventCode, session);
        
        // Check vendor restriction FIRST (for oneVendorOnly policy)
        if (ticket != null && ticket.getEvent().getCode().equals(eventCode)) {
            CartService.CheckResult vendorCheck = cartService.canAddToCart(eventCode, ticket, vendor, qty);
            if (!vendorCheck.allowed()) {
                if (isHx(request)) {
                    populateCartModel(eventCode, cart, model);
                    model.addAttribute("errorMessage", vendorCheck.message());
                    return CART_FRAGMENT;
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", vendorCheck.message());
                    return "redirect:/e/" + eventCode + "/v/" + vendor.getId();
                }
            }
        }
        
        // Check if this ticket has category limits set
        if (ticket != null && ticket.getEvent().getCode().equals(eventCode)) {
            // Get existing items in cart for this vendor
            Map<Long, Integer> vendorCart = cart.getAll().get(vendor.getId());
            
            // Build category totals map including the NEW item we're trying to add
            Map<String, Integer> categoryTotals = new HashMap<>();
            
            // First, add existing cart items
            if (vendorCart != null) {
                for (Map.Entry<Long, Integer> entry : vendorCart.entrySet()) {
                    MenuItem existingItem = menuItemRepo.findById(entry.getKey()).orElse(null);
                    if (existingItem != null) {
                        String existingCategory = existingItem.getCategory() != null ? existingItem.getCategory().trim() : "";
                        if (!existingCategory.isBlank()) {
                            categoryTotals.merge(existingCategory, entry.getValue(), Integer::sum);
                        }
                    }
                }
            }
            
            // Then, simulate adding the new item
            String newItemCategory = item.getCategory() != null ? item.getCategory().trim() : "";
            if (!newItemCategory.isBlank()) {
                categoryTotals.merge(newItemCategory, Math.max(1, qty), Integer::sum);
            }
            
            // Convert to CategoryItem list
            List<CartService.CategoryItem> itemsToAdd = categoryTotals.entrySet().stream()
                .map(e -> new CartService.CategoryItem(e.getKey(), e.getValue()))
                .toList();
            
            // Check if can add with category restrictions BEFORE adding to cart
            CartService.CheckResult check = cartService.canAddItemsWithCategories(
                eventCode, ticket, vendor, itemsToAdd
            );
            
            if (!check.allowed()) {
                // BLOCK the add operation and show error immediately
                if (isHx(request)) {
                    populateCartModel(eventCode, cart, model);
                    model.addAttribute("errorMessage", check.message());
                    return CART_FRAGMENT;
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", check.message());
                    return "redirect:/e/" + eventCode + "/v/" + vendor.getId();
                }
            }
        }

        // Only add to cart if all checks passed
        cart.add(vendor.getId(), item.getId(), Math.max(1, qty));
        
        // If HTMX request, return cart fragment for sidebar
        if (isHx(request)) {
            populateCartModel(eventCode, cart, model);
            model.addAttribute("successMessage", "✅ " + item.getName() + " added to cart!");
            return CART_FRAGMENT;
        }
        
        // Otherwise, redirect back to vendor menu (not cart)
        redirectAttributes.addFlashAttribute("successMessage", "✅ " + item.getName() + " added to cart!");
        return "redirect:/e/" + eventCode + "/v/" + vendor.getId();
    }

    @GetMapping
    public String view(@PathVariable String eventCode,
                       HttpSession session,
                       HttpServletRequest request,
                       Model model){
        CartSession cart = cart(session);
        populateCartModel(eventCode, cart, model);
        return isHx(request) ? CART_FRAGMENT : "cart";
    }

    @PostMapping("/remove-group")
    public String removeGroup(@PathVariable String eventCode,
                              @RequestParam Long vendorId,
                              HttpSession session,
                              HttpServletRequest request,
                              Model model){
        CartSession cart = cart(session);
        cart.removeVendorGroup(vendorId);
        populateCartModel(eventCode, cart, model);
        model.addAttribute("successMessage", "Vendor removed from cart.");
        return isHx(request) ? CART_FRAGMENT : "redirect:/e/" + eventCode + "/cart";
    }

    @PostMapping("/clear")
    public String clear(@PathVariable String eventCode,
                        HttpSession session,
                        HttpServletRequest request,
                        Model model){
        CartSession cart = cart(session);
        cart.clear();
        populateCartModel(eventCode, cart, model);
        model.addAttribute("successMessage", "Cart cleared.");
        return isHx(request) ? CART_FRAGMENT : "redirect:/e/" + eventCode + "/cart";
    }

    private Ticket activeTicket(String eventCode, HttpSession session) {
        Object attr = session.getAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR);
        if (attr instanceof Long ticketId) {
            return ticketRepo.findById(ticketId)
                    .filter(t -> t.getEvent().getCode().equals(eventCode))
                    .orElse(null);
        }
        return null;
    }
}
