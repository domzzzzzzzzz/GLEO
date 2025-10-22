package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.domain.TierPolicy;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.service.EventPolicyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/events")
public class AdminEventController {
    private final EventRepo eventRepo;
    private final EventPolicyService policyService;

    public AdminEventController(EventRepo eventRepo, EventPolicyService policyService){
        this.eventRepo = eventRepo;
        this.policyService = policyService;
    }

    @PostMapping
    public String createEvent(@RequestParam String code,
                              @RequestParam String name,
                              @RequestParam(required = false) String startAt,
                              @RequestParam(required = false) String endAt,
                              RedirectAttributes redirectAttributes){
        if (eventRepo.findByCode(code).isPresent()){
            redirectAttributes.addFlashAttribute("toastError", "Event code already exists.");
            return "redirect:/dashboard";
        }
        Event event = new Event();
        event.setCode(code);
        event.setName(name);
        try {
            event.setStartAt(startAt != null && !startAt.isBlank() ? LocalDateTime.parse(startAt) : LocalDateTime.now());
            event.setEndAt(endAt != null && !endAt.isBlank() ? LocalDateTime.parse(endAt) : LocalDateTime.now().plusHours(6));
        } catch (DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("toastError", "Invalid date format. Use ISO format (e.g. 2025-10-20T18:00).");
            return "redirect:/dashboard";
        }
        eventRepo.save(event);

        policyService.updateTierPolicy(event.getCode(), TierCode.VIP, true, null);
        policyService.updateTierPolicy(event.getCode(), TierCode.REG, false, 1);

        redirectAttributes.addFlashAttribute("toastMessage", "Event '" + name + "' created successfully.");
        return "redirect:/dashboard";
    }

    @GetMapping("/{eventCode}/policies")
    public String policies(@PathVariable String eventCode, Model model){
        var e = policyService.get(eventCode);
        model.addAttribute("event", e);
        Map<TierCode, TierPolicy> tierPolicies = policyService.tierPolicies(eventCode).stream()
                .collect(Collectors.toMap(TierPolicy::getTierCode, tp -> tp, (a, b) -> a, () -> new EnumMap<>(TierCode.class)));
        model.addAttribute("tierPolicies", tierPolicies);
        model.addAttribute("tiers", TierCode.values());
        return "event_policies";
    }

    @PostMapping("/{eventCode}/policy")
    public String update(@PathVariable String eventCode,
                         @RequestParam Map<String,String> form,
                         RedirectAttributes redirectAttributes){
        var e = policyService.get(eventCode);
        e.setEnableGuestPickupConfirm(form.containsKey("enableGuestPickupConfirm"));
        e.setRequireVendorPinForPickup(form.containsKey("requireVendorPinForPickup"));
        e.setEnableMultiVendorCart(form.containsKey("enableMultiVendorCart"));
        e.setBlockAddWhenOpenOrder(form.containsKey("blockAddWhenOpenOrder"));
        e.setRegularOneItemPerVendor(form.containsKey("regularOneItemPerVendor"));
        eventRepo.save(e);
        redirectAttributes.addFlashAttribute("toastMessage", "Policies updated successfully.");
        return "redirect:/admin/events/" + eventCode + "/policies";
    }

    @PostMapping("/{eventCode}/tier-policy")
    public String updateTierPolicy(@PathVariable String eventCode,
                                   @RequestParam TierCode tierCode,
                                   @RequestParam String accessMode,
                                   @RequestParam(required = false) Integer maxItemsPerVendor,
                                   RedirectAttributes redirectAttributes){
        boolean unlimited = "full".equalsIgnoreCase(accessMode);
        if (!unlimited) {
            if (maxItemsPerVendor == null || maxItemsPerVendor < 1) {
                redirectAttributes.addFlashAttribute("toastError", "Please provide a positive limit for this tier.");
                return "redirect:/admin/events/" + eventCode + "/policies";
            }
        }
        policyService.updateTierPolicy(eventCode, tierCode, unlimited, unlimited ? null : maxItemsPerVendor);
        redirectAttributes.addFlashAttribute("toastMessage", "Tier policy updated for " + tierCode + ".");
        return "redirect:/admin/events/" + eventCode + "/policies";
    }
}
