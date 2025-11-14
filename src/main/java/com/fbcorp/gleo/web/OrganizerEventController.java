package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.AuditLogEntry;
import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.UserAccountRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.service.AssetStorageService;
import com.fbcorp.gleo.service.AuditLogService;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.TicketImportLogService;
import com.fbcorp.gleo.service.TicketImportService;
import com.fbcorp.gleo.service.OrganizerAnalyticsService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/organizer/events")
public class OrganizerEventController {

    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final EventPolicyService policyService;
    private final TicketImportService ticketImportService;
    private final AssetStorageService assetStorageService;
    private final TicketImportLogService importLogService;
    private final OrganizerAnalyticsService analyticsService;
    private final AuditLogService auditLogService;
    private final UserAccountRepo userAccountRepo;

    public OrganizerEventController(VendorRepo vendorRepo,
                                    MenuItemRepo menuItemRepo,
                                    EventPolicyService policyService,
                                    TicketImportService ticketImportService,
                                    AssetStorageService assetStorageService,
                                    TicketImportLogService importLogService,
                                    OrganizerAnalyticsService analyticsService,
                                    AuditLogService auditLogService,
                                    UserAccountRepo userAccountRepo) {
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.policyService = policyService;
        this.ticketImportService = ticketImportService;
        this.assetStorageService = assetStorageService;
        this.importLogService = importLogService;
        this.analyticsService = analyticsService;
        this.auditLogService = auditLogService;
        this.userAccountRepo = userAccountRepo;
    }

    @PostMapping("/{eventCode}/vendors")
    public String addVendor(@PathVariable String eventCode,
                            @RequestParam String name,
                            @RequestParam(required = false) String pin,
                            @RequestParam(required = false, name = "image") MultipartFile imageFile,
                            RedirectAttributes redirectAttributes){
        Event event = policyService.get(eventCode);
        String trimmedName = name != null ? name.trim() : "";
        if (!StringUtils.hasText(trimmedName)) {
            redirectAttributes.addFlashAttribute("toastError", "Vendor name is required.");
            return "redirect:/dashboard";
        }
        Vendor vendor = new Vendor();
        vendor.setEvent(event);
        vendor.setName(trimmedName);
        vendor.setPinPlain(pin);
        if (imageFile != null && !imageFile.isEmpty()) {
            String contentType = imageFile.getContentType();
            if (contentType != null && !contentType.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("toastError", "Please upload an image file (JPG, PNG, or GIF).");
                return "redirect:/dashboard";
            }
            try {
                String storedPath = assetStorageService.storeVendorImage(imageFile);
                vendor.setImagePath(storedPath);
            } catch (IOException | IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("toastError", "Failed to store image: " + ex.getMessage());
                return "redirect:/dashboard";
            }
        }
        vendorRepo.save(vendor);
        auditLogService.record(AuditLogEntry.Category.VENDOR,
                "Added vendor '" + trimmedName + "'",
                currentUsername());
        redirectAttributes.addFlashAttribute("toastMessage", "Vendor '" + trimmedName + "' added to event.");
        return "redirect:/dashboard";
    }

    @PostMapping("/{eventCode}/tickets/upload")
    public String uploadTickets(@PathVariable String eventCode,
                                @RequestParam("file") MultipartFile file,
                                RedirectAttributes redirectAttributes){
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError", "Please select a CSV or Excel file to upload.");
            return "redirect:/dashboard";
        }
        Event event = policyService.get(eventCode);
        TicketImportService.ImportResult result;
        try {
            result = ticketImportService.importSheet(event, file);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("toastError", "Failed to read sheet: " + e.getMessage());
            return "redirect:/dashboard";
        }
        redirectAttributes.addFlashAttribute("importResult", result);
        String summary = result.summaryMessage();
        if (result.created() > 0) {
            redirectAttributes.addFlashAttribute("toastMessage", summary);
        } else {
            redirectAttributes.addFlashAttribute("toastError", summary);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        importLogService.record(event, username, result);

        return "redirect:/dashboard";
    }

    @GetMapping("/{eventCode}/tickets/template")
    public ResponseEntity<ByteArrayResource> downloadTemplate(@PathVariable String eventCode) {
        policyService.get(eventCode);
        byte[] csv = ticketImportService.generateCsvTemplate();
        var resource = new ByteArrayResource(csv);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ticket_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(csv.length)
                .body(resource);
    }

    @GetMapping("/{eventCode}/vendors/export")
    public ResponseEntity<ByteArrayResource> exportVendors(@PathVariable String eventCode) {
        Event event = policyService.get(eventCode);
        List<Vendor> vendors = vendorRepo.findByEvent(event);
        Map<Long, OrganizerAnalyticsService.VendorStats> stats = analyticsService.computeVendorStats(event, vendors);
        StringBuilder csv = new StringBuilder();
        csv.append("Vendor,Status,Pickup PIN,Total orders,Completed orders,Items served,Revenue (EGP)\n");
        for (Vendor vendor : vendors) {
            OrganizerAnalyticsService.VendorStats vendorStats = stats.getOrDefault(
                    vendor.getId(),
                    new OrganizerAnalyticsService.VendorStats(vendor, 0, 0, 0, BigDecimal.ZERO));
            csv.append(escapeCsv(vendor.getName())).append(',');
            csv.append(escapeCsv(vendor.isActive() ? "Active" : "Archived")).append(',');
            csv.append(escapeCsv(vendor.getPinPlain() != null ? vendor.getPinPlain() : "")).append(',');
            csv.append(vendorStats.totalOrders()).append(',');
            csv.append(vendorStats.completedOrders()).append(',');
            csv.append(vendorStats.itemsServed()).append(',');
        csv.append(vendorStats.revenue().setScale(2, RoundingMode.HALF_UP)).append('\n');
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        auditLogService.record(AuditLogEntry.Category.VENDOR,
                "Exported vendor roster for '" + (event.getName() != null ? event.getName() : event.getCode()) + "' (" + vendors.size() + " vendors)",
                currentUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vendor_roster.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    @PostMapping("/{eventCode}/vendors/{vendorId}/menu")
    public String saveMenuItem(@PathVariable String eventCode,
                               @PathVariable Long vendorId,
                               @RequestParam("name") String name,
                               @RequestParam("price") String priceRaw,
                               @RequestParam(value = "maxPerOrder", required = false) Integer maxPerOrder,
                               @RequestParam(value = "category", required = false) String category,
                               @RequestParam(value = "categoryOrder", required = false) Integer categoryOrder,
                               @RequestParam(value = "image", required = false) MultipartFile imageFile,
                               @RequestParam(value = "menuItemId", required = false) Long menuItemId,
                               @RequestParam(value = "removeImage", defaultValue = "false") boolean removeImage,
                               RedirectAttributes redirectAttributes) {
        return upsertMenuItem(eventCode, vendorId, menuItemId, name, priceRaw, maxPerOrder, category, categoryOrder, imageFile, removeImage, redirectAttributes);
    }

    @PostMapping("/{eventCode}/vendors/{vendorId}/menu/{menuItemId}/edit")
    public String editMenuItem(@PathVariable String eventCode,
                               @PathVariable Long vendorId,
                               @PathVariable Long menuItemId,
                               @RequestParam("name") String name,
                               @RequestParam("price") String priceRaw,
                               @RequestParam(value = "maxPerOrder", required = false) Integer maxPerOrder,
                               @RequestParam(value = "category", required = false) String category,
                               @RequestParam(value = "categoryOrder", required = false) Integer categoryOrder,
                               @RequestParam(value = "image", required = false) MultipartFile imageFile,
                               @RequestParam(value = "removeImage", defaultValue = "false") boolean removeImage,
                               RedirectAttributes redirectAttributes) {
        return upsertMenuItem(eventCode, vendorId, menuItemId, name, priceRaw, maxPerOrder, category, categoryOrder, imageFile, removeImage, redirectAttributes);
    }

    private String upsertMenuItem(String eventCode,
                                  Long vendorId,
                                  Long menuItemId,
                                  String name,
                                  String priceRaw,
                                  Integer maxPerOrder,
                                  String category,
                                  Integer categoryOrder,
                                  MultipartFile imageFile,
                                  boolean removeImage,
                                  RedirectAttributes redirectAttributes) {
        Event event = policyService.get(eventCode);
        Vendor vendor = vendorRepo.findById(vendorId).orElse(null);
        if (vendor == null || !vendor.getEvent().getId().equals(event.getId())) {
            redirectAttributes.addFlashAttribute("toastError", "Vendor not found for this event.");
            return "redirect:/dashboard";
        }
        String trimmedName = name != null ? name.trim() : "";
        if (trimmedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError", "Menu item name is required.");
            return "redirect:/dashboard";
        }
        if (priceRaw == null || priceRaw.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError", "Price is required.");
            return "redirect:/dashboard";
        }

        BigDecimal price;
        try {
            price = new BigDecimal(priceRaw.trim());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("toastError", "Invalid price value.");
            return "redirect:/dashboard";
        }
        if (price.scale() > 2) {
            price = price.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            redirectAttributes.addFlashAttribute("toastError", "Price cannot be negative.");
            return "redirect:/dashboard";
        }

        price = price.setScale(2, RoundingMode.HALF_UP);

        MenuItem menuItem;
        boolean isNew = (menuItemId == null);
        if (isNew) {
            menuItem = new MenuItem();
            menuItem.setVendor(vendor);
            menuItem.setAvailable(true);
        } else {
            menuItem = menuItemRepo.findById(menuItemId).orElse(null);
            if (menuItem == null || !menuItem.getVendor().getId().equals(vendor.getId())) {
                redirectAttributes.addFlashAttribute("toastError", "Menu item not found for this vendor.");
                return "redirect:/dashboard";
            }
        }

        menuItem.setName(trimmedName);
        menuItem.setPrice(price);
        menuItem.setMaxPerOrder(maxPerOrder);
        // Normalize category (empty -> null)
        if (category != null) {
            String c = category.trim();
            menuItem.setCategory(c.isEmpty() ? null : c);
        } else {
            menuItem.setCategory(null);
        }
        // Category order: organizer may supply an integer to control category sorting.
        menuItem.setCategoryOrder(categoryOrder == null ? 0 : categoryOrder);

        if (!isNew && removeImage) {
            menuItem.setImagePath(null);
        } else if (imageFile != null && !imageFile.isEmpty()) {
            String contentType = imageFile.getContentType();
            if (contentType != null && !contentType.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("toastError", "Please upload an image file (JPG, PNG, or GIF).");
                return "redirect:/dashboard";
            }
            try {
                String storedPath = assetStorageService.storeMenuItemImage(imageFile);
                menuItem.setImagePath(storedPath);
            } catch (IOException | IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("toastError", "Failed to store image: " + ex.getMessage());
                return "redirect:/dashboard";
            }
        }

        menuItemRepo.save(menuItem);

        if (isNew) {
            menuItem.setAvailable(true);
            redirectAttributes.addFlashAttribute("toastMessage", "Menu item '" + trimmedName + "' added to " + vendor.getName() + ".");
            auditLogService.record(AuditLogEntry.Category.MENU,
                    "Added menu item '" + trimmedName + "' (" + price + " EGP) to " + vendor.getName(),
                    currentUsername());
        } else {
            redirectAttributes.addFlashAttribute("toastMessage", "Menu item '" + trimmedName + "' updated.");
            auditLogService.record(
                    AuditLogEntry.Category.MENU,
                    "Updated menu item '" + trimmedName + "' for vendor '" + vendor.getName() + "'",
                    currentUsername());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/{eventCode}/vendors/{vendorId}/menu/{menuItemId}/delete")
    public String deleteMenuItem(@PathVariable String eventCode,
                                 @PathVariable Long vendorId,
                                 @PathVariable Long menuItemId,
                                 RedirectAttributes redirectAttributes) {
        Event event = policyService.get(eventCode);
        Vendor vendor = vendorRepo.findById(vendorId).orElse(null);
        if (vendor == null || !vendor.getEvent().getId().equals(event.getId())) {
            redirectAttributes.addFlashAttribute("toastError", "Vendor not found for this event.");
            return "redirect:/dashboard";
        }
        MenuItem menuItem = menuItemRepo.findById(menuItemId).orElse(null);
        if (menuItem == null || !menuItem.getVendor().getId().equals(vendor.getId())) {
            redirectAttributes.addFlashAttribute("toastError", "Menu item not found for this vendor.");
            return "redirect:/dashboard";
        }

        String itemName = menuItem.getName() != null ? menuItem.getName() : "Menu item";
        menuItemRepo.delete(menuItem);
        auditLogService.record(
                AuditLogEntry.Category.MENU,
                "Deleted menu item '" + itemName + "' from vendor '" + vendor.getName() + "'",
                currentUsername());
        redirectAttributes.addFlashAttribute("toastMessage", itemName + " deleted.");
        return "redirect:/dashboard";
    }

    @PostMapping("/{eventCode}/vendors/{vendorId}/delete")
    public String deleteVendorPost(@PathVariable String eventCode,
                                   @PathVariable Long vendorId,
                                   RedirectAttributes redirectAttributes) {
        return removeVendor(eventCode, vendorId, redirectAttributes);
    }

    @GetMapping("/{eventCode}/vendors/{vendorId}/delete")
    public String deleteVendorGet(@PathVariable String eventCode,
                                  @PathVariable Long vendorId,
                                  RedirectAttributes redirectAttributes) {
        return removeVendor(eventCode, vendorId, redirectAttributes);
    }

    private String removeVendor(String eventCode, Long vendorId, RedirectAttributes redirectAttributes) {
        Event event = policyService.get(eventCode);
        Vendor vendor = vendorRepo.findById(vendorId).orElse(null);
        if (vendor == null || !vendor.getEvent().getId().equals(event.getId())) {
            redirectAttributes.addFlashAttribute("toastError", "Vendor not found for this event.");
            return "redirect:/dashboard";
        }
        if (!vendor.isActive()) {
            redirectAttributes.addFlashAttribute("toastMessage", "Vendor '" + vendor.getName() + "' is already inactive.");
            return "redirect:/dashboard";
        }

        archiveVendor(vendor);
        auditLogService.record(AuditLogEntry.Category.VENDOR,
                "Archived vendor '" + vendor.getName() + "'",
                currentUsername());
        redirectAttributes.addFlashAttribute("toastMessage", "Vendor '" + vendor.getName() + "' has been removed from the lineup.");
        return "redirect:/dashboard";
    }

    @PostMapping("/{eventCode}/vendors/{vendorId}/edit")
    public String editVendor(@PathVariable String eventCode,
                             @PathVariable Long vendorId,
                             @RequestParam String name,
                             @RequestParam(required = false) String pin,
                             @RequestParam(value = "image", required = false) MultipartFile newImage,
                             @RequestParam(value = "removeImage", required = false, defaultValue = "false") boolean removeImage,
                             RedirectAttributes redirectAttributes) {
        Event event = policyService.get(eventCode);
        Vendor vendor = vendorRepo.findById(vendorId).orElse(null);
        if (vendor == null || !vendor.getEvent().getId().equals(event.getId())) {
            redirectAttributes.addFlashAttribute("toastError", "Vendor not found for this event.");
            return "redirect:/dashboard";
        }
        String trimmedName = name != null ? name.trim() : "";
        if (!StringUtils.hasText(trimmedName)) {
            redirectAttributes.addFlashAttribute("toastError", "Vendor name is required.");
            return "redirect:/dashboard";
        }

        List<String> changeNotes = new ArrayList<>();
        if (!Objects.equals(vendor.getName(), trimmedName)) {
            changeNotes.add("renamed");
        }
        vendor.setName(trimmedName);

        String normalizedPin = pin != null && !pin.isBlank() ? pin.trim() : null;
        String previousPin = vendor.getPinPlain();
        if (!Objects.equals(previousPin, normalizedPin)) {
            if (normalizedPin != null) {
                changeNotes.add(previousPin == null ? "added pickup PIN" : "updated pickup PIN");
            } else if (previousPin != null) {
                changeNotes.add("cleared pickup PIN");
            }
            vendor.setPinPlain(normalizedPin);
        }

        String previousImage = vendor.getImagePath();
        boolean removedImage = false;
        boolean updatedImage = false;

        if (removeImage && previousImage != null) {
            vendor.setImagePath(null);
            removedImage = true;
        } else if (newImage != null && !newImage.isEmpty()) {
            String contentType = newImage.getContentType();
            if (contentType != null && !contentType.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("toastError", "Please upload an image file (JPG, PNG, or GIF).");
                return "redirect:/dashboard";
            }
            try {
                String storedPath = assetStorageService.storeVendorImage(newImage);
                vendor.setImagePath(storedPath);
                updatedImage = true;
            } catch (IOException | IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("toastError", "Failed to store image: " + ex.getMessage());
                return "redirect:/dashboard";
            }
        }

        if (removedImage) {
            changeNotes.add("removed image");
        } else if (updatedImage) {
            changeNotes.add(previousImage == null ? "added image" : "updated image");
        }

        vendorRepo.save(vendor);
        String auditMessage = changeNotes.isEmpty()
                ? "Updated vendor '" + vendor.getName() + "'"
                : "Updated vendor '" + vendor.getName() + "' (" + String.join(", ", changeNotes) + ")";
        auditLogService.record(AuditLogEntry.Category.VENDOR, auditMessage, currentUsername());
        redirectAttributes.addFlashAttribute("toastMessage", "Vendor '" + vendor.getName() + "' updated.");
        return "redirect:/dashboard";
    }

    @PostMapping("/{eventCode}/vendors/{vendorId}/restore")
    public String restoreVendor(@PathVariable String eventCode,
                                @PathVariable Long vendorId,
                                RedirectAttributes redirectAttributes) {
        Event event = policyService.get(eventCode);
        Vendor vendor = vendorRepo.findById(vendorId).orElse(null);
        if (vendor == null || !vendor.getEvent().getId().equals(event.getId())) {
            redirectAttributes.addFlashAttribute("toastError", "Vendor not found for this event.");
            return "redirect:/dashboard";
        }
        if (vendor.isActive()) {
            redirectAttributes.addFlashAttribute("toastMessage", "Vendor '" + vendor.getName() + "' is already active.");
            return "redirect:/dashboard";
        }
        activateVendor(vendor);
        auditLogService.record(AuditLogEntry.Category.VENDOR,
                "Restored vendor '" + vendor.getName() + "'",
                currentUsername());
        redirectAttributes.addFlashAttribute("toastMessage", "Vendor '" + vendor.getName() + "' restored.");
        return "redirect:/dashboard";
    }

    @PostMapping("/{eventCode}/vendors/{vendorId}/delete-permanent")
    public String deleteVendorPermanent(@PathVariable String eventCode,
                                        @PathVariable Long vendorId,
                                        RedirectAttributes redirectAttributes) {
        Event event = policyService.get(eventCode);
        Vendor vendor = vendorRepo.findById(vendorId).orElse(null);
        if (vendor == null || !vendor.getEvent().getId().equals(event.getId())) {
            redirectAttributes.addFlashAttribute("toastError", "Vendor not found for this event.");
            return "redirect:/dashboard";
        }
        menuItemRepo.deleteByVendor(vendor);
        var linkedUsers = userAccountRepo.findByVendor(vendor);
        if (!linkedUsers.isEmpty()) {
            userAccountRepo.deleteAll(linkedUsers);
        }
        String vendorName = vendor.getName();
        vendorRepo.delete(vendor);
        auditLogService.record(AuditLogEntry.Category.VENDOR,
                "Deleted vendor '" + vendorName + "' permanently",
                currentUsername());
        redirectAttributes.addFlashAttribute("toastMessage", "Vendor '" + vendorName + "' deleted permanently.");
        return "redirect:/dashboard";
    }

    @PostMapping("/{eventCode}/vendors/bulk")
    public String bulkUpdate(@PathVariable String eventCode,
                             @RequestParam String action,
                             @RequestParam(value = "vendorIds", required = false) List<Long> vendorIds,
                             @RequestParam(value = "vendorQuery", required = false) String vendorQuery,
                             @RequestParam(value = "vendorStatus", required = false) String vendorStatus,
                             @RequestParam(value = "vendorSort", required = false) String vendorSort,
                             RedirectAttributes redirectAttributes) {
        if (vendorIds == null || vendorIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError", "Select at least one vendor before applying a bulk action.");
            return "redirect:/dashboard";
        }
        String normalizedAction = action != null ? action.trim().toLowerCase(Locale.ROOT) : "";
        if (!normalizedAction.equals("activate") && !normalizedAction.equals("archive")) {
            redirectAttributes.addFlashAttribute("toastError", "Unsupported bulk action selected.");
            return "redirect:/dashboard";
        }
        Event event = policyService.get(eventCode);
        int processed = 0;
        for (Long vendorId : vendorIds) {
            Vendor vendor = vendorRepo.findById(vendorId).orElse(null);
            if (vendor == null || !vendor.getEvent().getId().equals(event.getId())) {
                continue;
            }
            if ("archive".equals(normalizedAction)) {
                if (vendor.isActive()) {
                    archiveVendor(vendor);
                    processed++;
                }
            } else {
                if (!vendor.isActive()) {
                    activateVendor(vendor);
                    processed++;
                }
            }
        }
        if (processed > 0) {
            redirectAttributes.addFlashAttribute("toastMessage", processed + " vendor(s) updated.");
            String actionMessage = "activate".equals(normalizedAction)
                    ? "Bulk activated " + processed + " vendor(s)"
                    : "Bulk archived " + processed + " vendor(s)";
            auditLogService.record(AuditLogEntry.Category.VENDOR, actionMessage, currentUsername());
        } else {
            redirectAttributes.addFlashAttribute("toastError", "No vendors were updated by that action.");
        }
        if (StringUtils.hasText(vendorQuery)) {
            redirectAttributes.addAttribute("vendorQuery", vendorQuery);
        }
        if (StringUtils.hasText(vendorStatus)) {
            redirectAttributes.addAttribute("vendorStatus", vendorStatus);
        }
        if (StringUtils.hasText(vendorSort)) {
            redirectAttributes.addAttribute("vendorSort", vendorSort);
        }
        return "redirect:/dashboard";
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private void archiveVendor(Vendor vendor) {
        if (vendor.isActive()) {
            vendor.setActive(false);
            vendorRepo.save(vendor);
        }
        List<MenuItem> items = menuItemRepo.findByVendorOrderByNameAsc(vendor);
        if (!items.isEmpty()) {
            items.forEach(item -> item.setAvailable(false));
            menuItemRepo.saveAll(items);
        }
    }

    private void activateVendor(Vendor vendor) {
        if (!vendor.isActive()) {
            vendor.setActive(true);
            vendorRepo.save(vendor);
        }
        List<MenuItem> items = menuItemRepo.findByVendorOrderByNameAsc(vendor);
        if (!items.isEmpty()) {
            items.forEach(item -> item.setAvailable(true));
            menuItemRepo.saveAll(items);
        }
    }
}
