package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Order;
import com.fbcorp.gleo.domain.TierPolicy;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.repo.OrderRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.repo.UserAccountRepo;
import com.fbcorp.gleo.service.EventPolicyService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
public class DashboardController {

    private final UserAccountRepo userAccountRepo;
    private final EventPolicyService policyService;
    private final OrderRepo orderRepo;
    private final EventRepo eventRepo;
    private final VendorRepo vendorRepo;

    public DashboardController(UserAccountRepo userAccountRepo,
                               EventPolicyService policyService,
                               OrderRepo orderRepo,
                               EventRepo eventRepo,
                               VendorRepo vendorRepo) {
        this.userAccountRepo = userAccountRepo;
        this.policyService = policyService;
        this.orderRepo = orderRepo;
        this.eventRepo = eventRepo;
        this.vendorRepo = vendorRepo;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "redirect:/login";
        }
        var account = userAccountRepo.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("user", account);
        model.addAttribute("authorities", authentication.getAuthorities());
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        boolean isOrganizer = hasRole(authentication, "ROLE_ORGANIZER");
        boolean isVendor = hasRole(authentication, "ROLE_VENDOR") || hasRole(authentication, "ROLE_STAFF");

        if (isAdmin) {
            model.addAttribute("events", eventRepo.findAll());
            return "dashboard/admin_dashboard";
        }

        if (isOrganizer){
            if (account != null && account.getEvent() != null) {
                var event = account.getEvent();
                model.addAttribute("event", event);
                List<TierPolicy> tierPolicies = policyService.tierPolicies(event.getCode());
                model.addAttribute("tierPolicies", tierPolicies);
                model.addAttribute("vendors", vendorRepo.findByEventAndActiveTrue(event));
                var orders = orderRepo.findByEvent(event);
                model.addAttribute("orders", orders);
                var statusSummary = orders.stream()
                        .collect(java.util.stream.Collectors.groupingBy(Order::getStatus, java.util.stream.Collectors.counting()));
                model.addAttribute("statusSummary", statusSummary);
            }
            return "dashboard/organizer_dashboard";
        }

        if (isVendor){
            if (account != null && account.getVendor() != null){
                model.addAttribute("orders", orderRepo.findByVendor(account.getVendor()));
            }
            return "dashboard/vendor_dashboard";
        }
        return "dashboard/generic_dashboard";
    }

    @RequestMapping("/organizer")
    public String organizerRoot(){
        return "redirect:/dashboard";
    }

    @RequestMapping("/vendor")
    public String vendorRoot(){
        return "redirect:/dashboard";
    }

    private boolean hasRole(Authentication auth, String role){
        for (GrantedAuthority authority : auth.getAuthorities()){
            if (authority.getAuthority().equals(role)){
                return true;
            }
        }
        return false;
    }
}
