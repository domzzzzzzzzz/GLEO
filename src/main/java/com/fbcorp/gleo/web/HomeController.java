package com.fbcorp.gleo.web;

import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.service.CartViewService;
import com.fbcorp.gleo.service.EventPolicyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/e/{eventCode}")
public class HomeController {

    private final VendorRepo vendorRepo;
    private final EventPolicyService policyService;
    private final CartViewService cartViewService;

    public HomeController(VendorRepo vendorRepo,
                          EventPolicyService policyService,
                          CartViewService cartViewService) {
        this.vendorRepo = vendorRepo;
        this.policyService = policyService;
        this.cartViewService = cartViewService;
    }

    @GetMapping
    public String landing(@PathVariable String eventCode, Model model, HttpSession session){
        var event = policyService.get(eventCode);
        model.addAttribute("event", event);
        model.addAttribute("vendors", vendorRepo.findByEventAndActiveTrue(event));
        model.addAttribute("cartSummary", cartViewService.summarize(getOrCreateCart(session)));
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

