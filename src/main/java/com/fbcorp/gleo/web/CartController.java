package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.service.CartViewService;
import com.fbcorp.gleo.service.EventPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Controller
@RequestMapping("/e/{eventCode}/cart")
public class CartController {

    private static final String CART_FRAGMENT = "fragments/cart_panel :: panel";

    private final MenuItemRepo menuItemRepo;
    private final VendorRepo vendorRepo;
    private final EventPolicyService policyService;
    private final CartViewService cartViewService;

    public CartController(MenuItemRepo menuItemRepo,
                          VendorRepo vendorRepo,
                          EventPolicyService policyService,
                          CartViewService cartViewService){
        this.menuItemRepo = menuItemRepo;
        this.vendorRepo = vendorRepo;
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

    @PostMapping("/add")
    public String add(@PathVariable String eventCode,
                      @RequestParam Long itemId,
                      @RequestParam(defaultValue = "1") int qty,
                      HttpSession session,
                      HttpServletRequest request,
                      Model model){
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
                populateCartModel(eventCode, cart, model);
                model.addAttribute("errorMessage", "This event allows orders from one vendor at a time.");
                return CART_FRAGMENT;
            }
        }

        cart.add(vendor.getId(), item.getId(), Math.max(1, qty));
        populateCartModel(eventCode, cart, model);
        model.addAttribute("successMessage", item.getName() + " added to cart.");

        return isHx(request) ? CART_FRAGMENT : "redirect:/e/" + eventCode + "/cart";
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
