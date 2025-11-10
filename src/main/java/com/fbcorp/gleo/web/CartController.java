package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.service.CartViewService;
import com.fbcorp.gleo.service.EventPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/e/{eventCode}/cart")
public class CartController {

    private static final String CART_FRAGMENT = "fragments/cart_panel :: panel";

    private final MenuItemRepo menuItemRepo;
    private final EventPolicyService policyService;
    private final CartViewService cartViewService;

    public CartController(MenuItemRepo menuItemRepo,
                          EventPolicyService policyService,
                          CartViewService cartViewService){
        this.menuItemRepo = menuItemRepo;
        this.policyService = policyService;
        this.cartViewService = cartViewService;
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
        model.addAttribute("event", event);
        model.addAttribute("isMultiVendor", policyService.multiVendorCart(eventCode));
        model.addAttribute("cartSummary", cartViewService.summarize(cartSession));
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
        } else {
            cart.setQty(vendorId, itemId, Math.min(qty, 99));
        }
        populateCartModel(eventCode, cart, model);
        // For HTMX from cart page, return full cart page; for sidebar, return fragment
        if (isHx(request)) {
            String hxTarget = request.getHeader("HX-Target");
            if ("cart-page".equals(hxTarget)) {
                return "cart"; // Full cart page for HTMX
            }
            return CART_FRAGMENT; // Sidebar fragment
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
        cart.setPromoCode(code);
        populateCartModel(eventCode, cart, model);
        model.addAttribute("successMessage", "Promo applied (if valid).");
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
        if (!policyService.multiVendorCart(eventCode)) {
            Map<Long, Map<Long, Integer>> lines = cart.getAll();
            if (!lines.isEmpty() && !lines.containsKey(vendor.getId())) {
                // Single-vendor policy violation
                if (isHx(request)) {
                    populateCartModel(eventCode, cart, model);
                    model.addAttribute("errorMessage", "This event allows orders from one vendor at a time.");
                    return CART_FRAGMENT;
                } else {
                    // Redirect back to vendor menu with flash error
                    redirectAttributes.addFlashAttribute("errorMessage", "This event allows orders from one vendor at a time.");
                    return "redirect:/e/" + eventCode + "/v/" + vendor.getId();
                }
            }
        }

        cart.add(vendor.getId(), item.getId(), Math.max(1, qty));
        
        // If HTMX request, return cart fragment for sidebar
        if (isHx(request)) {
            populateCartModel(eventCode, cart, model);
            model.addAttribute("successMessage", item.getName() + " added to cart.");
            return CART_FRAGMENT;
        }
        
        // Otherwise, redirect back to vendor menu (not cart)
        redirectAttributes.addFlashAttribute("successMessage", item.getName() + " added to cart!");
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
}
