package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.service.EventPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/e/{eventCode}/v")
public class VendorController {
    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final EventPolicyService policyService;

    public VendorController(VendorRepo vendorRepo, MenuItemRepo menuItemRepo, EventPolicyService policyService){
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.policyService = policyService;
    }

    @GetMapping("/{vendorId}")
    public String menu(@PathVariable String eventCode, @PathVariable Long vendorId, Model model){
        var event = policyService.get(eventCode);
        Vendor v = vendorRepo.findById(vendorId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!v.getEvent().getId().equals(event.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("event", event);
        model.addAttribute("vendor", v);
        model.addAttribute("items", menuItemRepo.findByVendorAndAvailableTrue(v));
        model.addAttribute("multiVendorEnabled", policyService.multiVendorCart(eventCode));
        return "vendor_menu";
    }
}
