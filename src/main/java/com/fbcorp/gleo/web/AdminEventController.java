package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.domain.TierPolicy;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.UserAccountRepo;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.AuditLogService;
import com.fbcorp.gleo.service.EventService;
import com.fbcorp.gleo.service.AssetStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;

@Controller
@RequestMapping("/admin/events")
public class AdminEventController {
    private final EventRepo eventRepo;
    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final UserAccountRepo userAccountRepo;
    private final EventPolicyService policyService;
    private final AuditLogService auditLogService;
    private final com.fbcorp.gleo.service.AdminPreferenceService adminPreferenceService;
    private final EventService eventService;
    private final AssetStorageService assetStorageService;

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

    public AdminEventController(EventRepo eventRepo,
                                VendorRepo vendorRepo,
                                MenuItemRepo menuItemRepo,
                                UserAccountRepo userAccountRepo,
                                EventPolicyService policyService,
                                AuditLogService auditLogService,
                                com.fbcorp.gleo.service.AdminPreferenceService adminPreferenceService,
                                EventService eventService,
                                AssetStorageService assetStorageService){
        this.eventRepo = eventRepo;
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.userAccountRepo = userAccountRepo;
        this.policyService = policyService;
        this.auditLogService = auditLogService;
        this.adminPreferenceService = adminPreferenceService;
        this.eventService = eventService;
        this.assetStorageService = assetStorageService;
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
    @GetMapping("/builder")
    public String eventBuilder(Model model) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            model.addAttribute("user", userAccountRepo.findByUsername(auth.getName()).orElse(null));
            adminPreferenceService.findByUsername(auth.getName()).ifPresent(pref -> {
                if (pref.getTheme() != null) model.addAttribute("adminTheme", pref.getTheme());
            });
        }
        return "admin/event_builder";
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

    @PreAuthorize("@permissionService.isAdmin(authentication)")
    @PostMapping(value = "/wizard", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> createEventViaWizard(@RequestBody WizardEventRequest request) {
        try {
            validateWizardRequest(request);

            if (eventRepo.findByCode(request.code.trim().toUpperCase()).isPresent()) {
                throw new IllegalArgumentException("Event code already exists.");
            }

            Event event = new Event();
            event.setCode(request.code.trim().toUpperCase());
            event.setName(request.name.trim());
            event.setStartAt(parseDateTime(request.startAt));
            event.setEndAt(parseDateTime(request.endAt));
            eventRepo.save(event);

            for (VendorInput vendorInput : request.vendors) {
                Vendor vendor = new Vendor();
                vendor.setEvent(event);
                vendor.setName(vendorInput.name.trim());
                vendor.setPinPlain(StringUtils.hasText(vendorInput.pin) ? vendorInput.pin.trim() : null);
                vendor.setActive(true);
                vendorRepo.save(vendor);

                for (MenuItemInput menuInput : vendorInput.menuItems) {
                    MenuItem menuItem = new MenuItem();
                    menuItem.setVendor(vendor);
                    menuItem.setName(menuInput.name.trim());
                    menuItem.setPrice(parsePrice(menuInput.price));
                    menuItem.setMaxPerOrder(menuInput.maxPerOrder);
                    menuItem.setAvailable(true);
                    menuItemRepo.save(menuItem);
                }
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "anonymous";
            auditLogService.record(com.fbcorp.gleo.domain.AuditLogEntry.Category.EVENT,
                    "Created event '" + event.getName() + "' with wizard",
                    username);

            return ResponseEntity.ok(Map.of("message", "Event '" + event.getName() + "' created successfully."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private void validateWizardRequest(WizardEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (!StringUtils.hasText(request.code) || !request.code.trim().toUpperCase().matches("^[A-Z][0-9]{4}$")) {
            throw new IllegalArgumentException("Event code must be 1 capital letter followed by 4 digits.");
        }
        if (!StringUtils.hasText(request.name)) {
            throw new IllegalArgumentException("Event name is required.");
        }
        if (request.vendors == null || request.vendors.isEmpty()) {
            throw new IllegalArgumentException("Add at least one vendor.");
        }
        if (request.vendors.size() > 8) {
            throw new IllegalArgumentException("You can add up to 8 vendors.");
        }
        for (VendorInput vendorInput : request.vendors) {
            if (!StringUtils.hasText(vendorInput.name)) {
                throw new IllegalArgumentException("Each vendor needs a name.");
            }
            if (vendorInput.menuItems == null || vendorInput.menuItems.isEmpty()) {
                throw new IllegalArgumentException("Each vendor must have at least one menu item.");
            }
            if (vendorInput.menuItems.size() > 5) {
                throw new IllegalArgumentException("Each vendor can have up to 5 menu items.");
            }
            for (MenuItemInput menuInput : vendorInput.menuItems) {
                if (!StringUtils.hasText(menuInput.name)) {
                    throw new IllegalArgumentException("Menu items need a name.");
                }
                parsePrice(menuInput.price);
            }
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date format. Use ISO date-time.");
        }
    }

    private BigDecimal parsePrice(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Menu item price is required.");
        }
        try {
            BigDecimal price = new BigDecimal(value.trim());
            if (price.scale() > 2) {
                price = price.setScale(2, RoundingMode.HALF_UP);
            }
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Menu item price must be zero or positive.");
            }
            return price;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Menu item price is invalid.");
        }
    }

    @GetMapping("/{eventCode}/policies")
    public String policies(@PathVariable String eventCode, Model model){
        var e = policyService.get(eventCode);
        model.addAttribute("event", e);
        Map<TierCode, TierPolicy> tierPolicies = policyService.tierPolicies(eventCode).stream()
                .collect(Collectors.toMap(TierPolicy::getTierCode, tp -> tp, (a, b) -> a, () -> new EnumMap<>(TierCode.class)));
        model.addAttribute("tierPolicies", tierPolicies);
        model.addAttribute("tiers", TierCode.values());
        
        // Build vendor-category structure
        List<Vendor> vendors = vendorRepo.findByEvent(e);
        List<Map<String, Object>> vendorCategoryData = new ArrayList<>();
        
        for (Vendor vendor : vendors) {
            List<MenuItem> items = menuItemRepo.findByVendorOrderByNameAsc(vendor);
            
            // Get distinct categories for this vendor
            Set<String> categories = items.stream()
                .map(item -> item.getCategory() == null || item.getCategory().isBlank() ? "Uncategorized" : item.getCategory())
                .collect(Collectors.toCollection(LinkedHashSet::new));
            
            if (!categories.isEmpty()) {
                Map<String, Object> vendorData = new HashMap<>();
                vendorData.put("vendorId", vendor.getId());
                vendorData.put("vendorName", vendor.getName());
                vendorData.put("categories", new ArrayList<>(categories));
                vendorCategoryData.add(vendorData);
            }
        }
        
        model.addAttribute("vendorCategoryData", vendorCategoryData);
        
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

    @GetMapping("/{eventCode}/vendors")
    public String manageVendors(@PathVariable String eventCode, Model model) {
        Event event = policyService.get(eventCode);
        List<Vendor> vendors = vendorRepo.findByEvent(event);
        
        model.addAttribute("event", event);
        model.addAttribute("vendors", vendors);
        
        return "admin/vendor_management";
    }

    @GetMapping("/{eventCode}/vendors/{vendorId}")
    public String vendorDetails(@PathVariable String eventCode, 
                                @PathVariable Long vendorId, 
                                Model model) {
        Event event = policyService.get(eventCode);
        Vendor vendor = vendorRepo.findById(vendorId)
            .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        
        if (!vendor.getEvent().getId().equals(event.getId())) {
            throw new IllegalArgumentException("Vendor does not belong to this event");
        }
        
        List<MenuItem> menuItems = menuItemRepo.findByVendorOrderByNameAsc(vendor);
        
        // Group menu items by category
        Map<String, List<MenuItem>> itemsByCategory = menuItems.stream()
            .collect(Collectors.groupingBy(
                item -> item.getCategory() != null && !item.getCategory().isBlank() 
                    ? item.getCategory() 
                    : "Uncategorized",
                java.util.LinkedHashMap::new,
                Collectors.toList()
            ));
        
        model.addAttribute("event", event);
        model.addAttribute("vendor", vendor);
        model.addAttribute("menuItems", menuItems);
        model.addAttribute("itemsByCategory", itemsByCategory);
        
        return "admin/vendor_details";
    }

    @PostMapping("/{eventCode}/vendors/{vendorId}/menu/add")
    public String addMenuItem(@PathVariable String eventCode,
                             @PathVariable Long vendorId,
                             @RequestParam String name,
                             @RequestParam String price,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) Integer maxPerOrder,
                             @RequestParam(required = false) MultipartFile image,
                             @RequestParam(required = false, defaultValue = "false") boolean available,
                             RedirectAttributes redirectAttributes) {
        try {
            Event event = policyService.get(eventCode);
            Vendor vendor = vendorRepo.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
            
            if (!vendor.getEvent().getId().equals(event.getId())) {
                throw new IllegalArgumentException("Vendor does not belong to this event");
            }

            MenuItem menuItem = new MenuItem();
            menuItem.setVendor(vendor);
            menuItem.setName(name.trim());
            menuItem.setPrice(new BigDecimal(price).setScale(2, RoundingMode.HALF_UP));
            menuItem.setCategory(category != null && !category.trim().isEmpty() ? category.trim() : null);
            menuItem.setMaxPerOrder(maxPerOrder);
            menuItem.setAvailable(available);

            // Handle image upload
            if (image != null && !image.isEmpty()) {
                String imagePath = assetStorageService.storeMenuItemImage(image);
                menuItem.setImagePath(imagePath);
            }

            menuItemRepo.save(menuItem);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "anonymous";
            auditLogService.record(com.fbcorp.gleo.domain.AuditLogEntry.Category.VENDOR,
                "Added menu item '" + name + "' to vendor " + vendor.getName() + " (event=" + eventCode + ")", username);

            redirectAttributes.addFlashAttribute("toastMessage", "Menu item added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("toastError", "Error adding menu item: " + e.getMessage());
        }

        return "redirect:/admin/events/" + eventCode + "/vendors/" + vendorId;
    }

    @PostMapping("/{eventCode}/vendors/{vendorId}/menu/{menuItemId}/edit")
    public String editMenuItem(@PathVariable String eventCode,
                              @PathVariable Long vendorId,
                              @PathVariable Long menuItemId,
                              @RequestParam String name,
                              @RequestParam String price,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) Integer maxPerOrder,
                              @RequestParam(required = false) MultipartFile image,
                              @RequestParam(required = false, defaultValue = "false") boolean available,
                              RedirectAttributes redirectAttributes) {
        try {
            Event event = policyService.get(eventCode);
            Vendor vendor = vendorRepo.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
            
            if (!vendor.getEvent().getId().equals(event.getId())) {
                throw new IllegalArgumentException("Vendor does not belong to this event");
            }

            MenuItem menuItem = menuItemRepo.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
            
            if (!menuItem.getVendor().getId().equals(vendorId)) {
                throw new IllegalArgumentException("Menu item does not belong to this vendor");
            }

            menuItem.setName(name.trim());
            menuItem.setPrice(new BigDecimal(price).setScale(2, RoundingMode.HALF_UP));
            menuItem.setCategory(category != null && !category.trim().isEmpty() ? category.trim() : null);
            menuItem.setMaxPerOrder(maxPerOrder);
            menuItem.setAvailable(available);

            // Handle image upload
            if (image != null && !image.isEmpty()) {
                String imagePath = assetStorageService.storeMenuItemImage(image);
                menuItem.setImagePath(imagePath);
            }

            menuItemRepo.save(menuItem);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "anonymous";
            auditLogService.record(com.fbcorp.gleo.domain.AuditLogEntry.Category.VENDOR,
                "Updated menu item '" + name + "' for vendor " + vendor.getName() + " (event=" + eventCode + ")", username);

            redirectAttributes.addFlashAttribute("toastMessage", "Menu item updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("toastError", "Error updating menu item: " + e.getMessage());
        }

        return "redirect:/admin/events/" + eventCode + "/vendors/" + vendorId;
    }

    @PostMapping("/{eventCode}/vendors/{vendorId}/menu/{menuItemId}/delete")
    public String deleteMenuItem(@PathVariable String eventCode,
                                @PathVariable Long vendorId,
                                @PathVariable Long menuItemId,
                                RedirectAttributes redirectAttributes) {
        try {
            Event event = policyService.get(eventCode);
            Vendor vendor = vendorRepo.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
            
            if (!vendor.getEvent().getId().equals(event.getId())) {
                throw new IllegalArgumentException("Vendor does not belong to this event");
            }

            MenuItem menuItem = menuItemRepo.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
            
            if (!menuItem.getVendor().getId().equals(vendorId)) {
                throw new IllegalArgumentException("Menu item does not belong to this vendor");
            }

            String itemName = menuItem.getName();
            menuItemRepo.delete(menuItem);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "anonymous";
            auditLogService.record(com.fbcorp.gleo.domain.AuditLogEntry.Category.VENDOR,
                "Deleted menu item '" + itemName + "' from vendor " + vendor.getName() + " (event=" + eventCode + ")", username);

            redirectAttributes.addFlashAttribute("toastMessage", "Menu item deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("toastError", "Error deleting menu item: " + e.getMessage());
        }

        return "redirect:/admin/events/" + eventCode + "/vendors/" + vendorId;
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
                                   @RequestParam(required = false, defaultValue = "false") boolean oneVendorOnly,
                                   @RequestParam(required = false, defaultValue = "false") boolean oneItemPerCategory,
                                   @RequestParam(required = false) Map<String, String> allParams,
                                   RedirectAttributes redirectAttributes){
        String normalizedMode = accessMode == null ? "unlimited" : accessMode.trim().toLowerCase();
        boolean unlimited = "unlimited".equals(normalizedMode);
        
        // Extract category limits from form parameters FIRST
        Map<String, Integer> categoryLimits = new HashMap<>();
        if (oneItemPerCategory) {
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("categoryLimit_")) {
                    String category = key.substring("categoryLimit_".length());
                    try {
                        int limit = Integer.parseInt(entry.getValue());
                        if (limit > 0) {
                            categoryLimits.put(category, limit);
                        }
                    } catch (NumberFormatException e) {
                        // Skip invalid values
                    }
                }
            }
        }
        
        if (unlimited) {
            oneVendorOnly = false;
            oneItemPerCategory = false;
            categoryLimits.clear();
        }

        // Validation: If not unlimited AND no category limits are set, then maxItemsPerVendor is required
        if (!unlimited && categoryLimits.isEmpty()) {
            if (maxItemsPerVendor == null || maxItemsPerVendor < 1) {
                redirectAttributes.addFlashAttribute("toastError", "Please provide a positive limit for this tier or set category limits.");
                return "redirect:/admin/events/" + eventCode + "/policies";
            }
        }
        
        policyService.updateTierPolicy(eventCode, tierCode, unlimited, unlimited ? null : maxItemsPerVendor, 
                                       oneVendorOnly, oneItemPerCategory, categoryLimits);

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
            auditLogService.record(
                    com.fbcorp.gleo.domain.AuditLogEntry.Category.EVENT,
                    "Deleted event '" + eventName + "'",
                    username);
            redirectAttributes.addFlashAttribute("toastMessage", "Event '" + eventName + "' deleted successfully.");
        } catch (Exception ex){
            redirectAttributes.addFlashAttribute("toastError", "Failed to delete event: " + ex.getMessage());
        }
        return "redirect:/dashboard";
    }

    private static class WizardEventRequest {
        public String code;
        public String name;
        public String startAt;
        public String endAt;
        public List<VendorInput> vendors;
    }

    private static class VendorInput {
        public String name;
        public String pin;
        public List<MenuItemInput> menuItems;
    }

    private static class MenuItemInput {
        public String name;
        public String price;
        public Integer maxPerOrder;
    }
}
