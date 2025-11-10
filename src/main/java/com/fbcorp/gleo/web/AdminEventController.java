package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.domain.TierPolicy;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.AuditLogService;
import com.fbcorp.gleo.service.EventService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final AuditLogService auditLogService;
    private final com.fbcorp.gleo.service.AdminPreferenceService adminPreferenceService;
    private final EventService eventService;

    @GetMapping("/policies")
    @PreAuthorize("@permissionService.isAdmin(authentication)")
    public String globalPolicies(Model model) {
        var events = eventRepo.findAll();
        for (Event event : events) {
            // Load tier policies for each event
            var tierPolicies = policyService.tierPolicies(event.getCode());
            event.setTierPolicies(tierPolicies);
        }
        model.addAttribute("events", events);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            adminPreferenceService.findByUsername(auth.getName()).ifPresent(pref -> {
                if (pref.getTheme() != null) model.addAttribute("adminTheme", pref.getTheme());
                if (pref.getMenuOrderJson() != null) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        var list = mapper.readValue(pref.getMenuOrderJson(), new TypeReference<java.util.List<MenuOrderItem>>(){});
                        model.addAttribute("adminMenuItems", list);
                    } catch (Exception ex) {
                        // ignore parse problems
                    }
                }
            });
        }
        return "admin/global_policies";
    }

    public AdminEventController(EventRepo eventRepo, EventPolicyService policyService, AuditLogService auditLogService, 
                               com.fbcorp.gleo.service.AdminPreferenceService adminPreferenceService, EventService eventService){
        this.eventRepo = eventRepo;
        this.policyService = policyService;
        this.auditLogService = auditLogService;
        this.adminPreferenceService = adminPreferenceService;
        this.eventService = eventService;
    }

    @PreAuthorize("@permissionService.isAdmin(authentication)")
    @GetMapping
    public String listEvents(Model model){
        var events = eventRepo.findAll();
        model.addAttribute("events", events);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            adminPreferenceService.findByUsername(auth.getName()).ifPresent(pref -> {
                if (pref.getTheme() != null) model.addAttribute("adminTheme", pref.getTheme());
                if (pref.getMenuOrderJson() != null) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        var list = mapper.readValue(pref.getMenuOrderJson(), new TypeReference<java.util.List<MenuOrderItem>>(){});
                        model.addAttribute("adminMenuItems", list);
                    } catch (Exception ex) { }
                }
            });
        }
        return "admin/events";
    }

    @PreAuthorize("@permissionService.isAdmin(authentication)")
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

    // Audit log
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String username = auth != null ? auth.getName() : "anonymous";
    auditLogService.record(com.fbcorp.gleo.domain.AuditLogEntry.Category.EVENT, "Created event: code=" + code + ", name=" + name, username);

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
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            adminPreferenceService.findByUsername(auth.getName()).ifPresent(pref -> {
                if (pref.getTheme() != null) model.addAttribute("adminTheme", pref.getTheme());
                if (pref.getMenuOrderJson() != null) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        var list = mapper.readValue(pref.getMenuOrderJson(), new TypeReference<java.util.List<MenuOrderItem>>(){});
                        model.addAttribute("adminMenuItems", list);
                    } catch (Exception ex) { }
                }
            });
        }
        return "admin/event_policies";
    }

    @PreAuthorize("@permissionService.isAdmin(authentication)")
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

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String username = auth != null ? auth.getName() : "anonymous";
    auditLogService.record(com.fbcorp.gleo.domain.AuditLogEntry.Category.EVENT, "Updated policies for event=" + eventCode, username);

        redirectAttributes.addFlashAttribute("toastMessage", "Policies updated successfully.");
        return "redirect:/admin/events/" + eventCode + "/policies";
    }

    @PreAuthorize("@permissionService.isAdmin(authentication)")
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

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String username = auth != null ? auth.getName() : "anonymous";
    auditLogService.record(com.fbcorp.gleo.domain.AuditLogEntry.Category.EVENT, "Updated tier policy for event=" + eventCode + ", tier=" + tierCode, username);

        redirectAttributes.addFlashAttribute("toastMessage", "Tier policy updated for " + tierCode + ".");
        return "redirect:/admin/events/" + eventCode + "/policies";
    }

    @PreAuthorize("@permissionService.isAdmin(authentication)")
    @PostMapping("/{eventCode}/delete")
    public String deleteEvent(@PathVariable String eventCode, RedirectAttributes redirectAttributes){
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "anonymous";
            String eventName = eventService.delete(eventCode);
            redirectAttributes.addFlashAttribute("toastMessage", "Event '" + eventName + "' deleted successfully.");
        } catch (Exception ex){
            redirectAttributes.addFlashAttribute("toastError", "Failed to delete event: " + ex.getMessage());
        }
        return "redirect:/dashboard";
    }
}
