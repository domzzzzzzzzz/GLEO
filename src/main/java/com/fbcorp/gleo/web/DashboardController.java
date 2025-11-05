package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.TierPolicy;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.OrderRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.repo.UserAccountRepo;
import com.fbcorp.gleo.service.AuditLogService;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.OrganizerAnalyticsService;
import com.fbcorp.gleo.service.TicketImportLogService;
import com.fbcorp.gleo.service.AdminPreferenceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final UserAccountRepo userAccountRepo;
    private final EventPolicyService policyService;
    private final OrderRepo orderRepo;
    private final EventRepo eventRepo;
    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final OrganizerAnalyticsService analyticsService;
    private final TicketImportLogService importLogService;
    private final AdminPreferenceService adminPreferenceService;
    private final AuditLogService auditLogService;

    public DashboardController(UserAccountRepo userAccountRepo,
                               EventPolicyService policyService,
                               OrderRepo orderRepo,
                               EventRepo eventRepo,
                               VendorRepo vendorRepo,
                               MenuItemRepo menuItemRepo,
                               OrganizerAnalyticsService analyticsService,
                               TicketImportLogService importLogService,
                               AuditLogService auditLogService,
                               AdminPreferenceService adminPreferenceService) {
        this.userAccountRepo = userAccountRepo;
        this.policyService = policyService;
        this.orderRepo = orderRepo;
        this.eventRepo = eventRepo;
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.analyticsService = analyticsService;
        this.importLogService = importLogService;
        this.auditLogService = auditLogService;
        this.adminPreferenceService = adminPreferenceService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model,
                             @RequestParam(value = "vendorQuery", required = false) String vendorQuery,
                             @RequestParam(value = "vendorStatus", required = false) String vendorStatus,
                             @RequestParam(value = "vendorSort", required = false) String vendorSort){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "redirect:/login";
        }
        var account = userAccountRepo.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("user", account);
        model.addAttribute("authorities", authentication.getAuthorities());
        boolean isAdmin = hasRole(authentication, "ADMIN");
        boolean isOrganizer = hasRole(authentication, "ORGANIZER");
        boolean isVendor = hasRole(authentication, "VENDOR") || hasRole(authentication, "STAFF");

        if (isAdmin) {
            model.addAttribute("events", eventRepo.findAll());
            // inject saved admin theme preference if present so the client can respect server preference
            adminPreferenceService.findByUsername(authentication.getName()).ifPresent(pref -> {
                if (pref.getTheme() != null) model.addAttribute("adminTheme", pref.getTheme());
                if (pref.getMenuOrderJson() != null) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        var list = mapper.readValue(pref.getMenuOrderJson(), new TypeReference<java.util.List<MenuOrderItem>>(){});
                        model.addAttribute("adminMenuItems", list);
                    } catch (Exception ex) {
                        // ignore parse errors; client-side fallback will apply
                    }
                }
            });
            return "dashboard/admin_dashboard";
        }

        if (isOrganizer){
            if (account != null && account.getEvent() != null) {
                var event = account.getEvent();
                model.addAttribute("event", event);
                List<TierPolicy> tierPolicies = policyService.tierPolicies(event.getCode());
                model.addAttribute("tierPolicies", tierPolicies);
                String normalizedQuery = vendorQuery != null ? vendorQuery.trim() : "";
                String normalizedStatus = vendorStatus != null ? vendorStatus.trim().toLowerCase(Locale.ROOT) : "all";
                String normalizedSort = vendorSort != null ? vendorSort.trim().toLowerCase(Locale.ROOT) : "name";

                List<Vendor> allVendors = vendorRepo.findByEvent(event);
                Map<Long, OrganizerAnalyticsService.VendorStats> vendorStatsMap = analyticsService.computeVendorStats(event, allVendors);
                long totalActiveVendors = allVendors.stream().filter(Vendor::isActive).count();
                long totalArchivedVendors = allVendors.size() - totalActiveVendors;
                model.addAttribute("totalActiveVendors", totalActiveVendors);
                model.addAttribute("totalArchivedVendors", totalArchivedVendors);

                Map<Long, List<MenuItem>> vendorMenusStore = new LinkedHashMap<>();
                for (Vendor vendor : allVendors) {
                    vendorMenusStore.put(vendor.getId(), menuItemRepo.findByVendorOrderByNameAsc(vendor));
                }

                BigDecimal totalRevenue = vendorStatsMap.values().stream()
                        .map(OrganizerAnalyticsService.VendorStats::revenue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                long totalCompletedOrders = vendorStatsMap.values().stream()
                        .mapToLong(OrganizerAnalyticsService.VendorStats::completedOrders).sum();
                long totalItemsSold = vendorStatsMap.values().stream()
                        .mapToLong(OrganizerAnalyticsService.VendorStats::itemsServed).sum();

                Map<Long, VendorSalesSummary> vendorSales = new LinkedHashMap<>();
                for (Vendor vendor : allVendors) {
                    OrganizerAnalyticsService.VendorStats stats = vendorStatsMap.getOrDefault(
                            vendor.getId(),
                            new OrganizerAnalyticsService.VendorStats(vendor, 0, 0, 0, BigDecimal.ZERO));
                    vendorSales.put(
                            vendor.getId(),
                            new VendorSalesSummary(
                                    vendor.getId(),
                                    vendor.getName(),
                                    stats.totalOrders(),
                                    stats.completedOrders(),
                                    stats.itemsServed(),
                                    stats.revenue()));
                }

                List<Vendor> filteredVendors = new ArrayList<>(allVendors);
                if (!normalizedQuery.isBlank()) {
                    String q = normalizedQuery.toLowerCase(Locale.ROOT);
                    filteredVendors.removeIf(v -> v.getName() == null || !v.getName().toLowerCase(Locale.ROOT).contains(q));
                }
                if ("active".equals(normalizedStatus)) {
                    filteredVendors.removeIf(v -> !v.isActive());
                } else if ("archived".equals(normalizedStatus)) {
                    filteredVendors.removeIf(Vendor::isActive);
                }
                if ("revenue".equals(normalizedSort)) {
                    filteredVendors.sort(Comparator.comparing(
                            (Vendor v) -> vendorSales.getOrDefault(
                                    v.getId(),
                                    new VendorSalesSummary(v.getId(), v.getName(), 0, 0, 0, BigDecimal.ZERO)
                            ).revenue()).reversed());
                } else {
                    filteredVendors.sort(Comparator.comparing(
                            v -> v.getName() != null ? v.getName().toLowerCase(Locale.ROOT) : ""));
                }

                Map<Long, List<MenuItem>> filteredMenus = new LinkedHashMap<>();
                for (Vendor vendor : filteredVendors) {
                    filteredMenus.put(vendor.getId(), vendorMenusStore.getOrDefault(vendor.getId(), List.of()));
                }
                List<Vendor> activeVendors = filteredVendors.stream()
                        .filter(Vendor::isActive)
                        .collect(Collectors.toList());
                List<Vendor> archivedVendors = filteredVendors.stream()
                        .filter(v -> !v.isActive())
                        .collect(Collectors.toList());

                model.addAttribute("vendorSales", vendorSales);
                model.addAttribute("vendorQuery", normalizedQuery);
                model.addAttribute("vendorStatus", normalizedStatus);
                model.addAttribute("vendorSort", normalizedSort);
                model.addAttribute("vendors", filteredVendors);
                model.addAttribute("vendorsActive", activeVendors);
                model.addAttribute("vendorsArchived", archivedVendors);
                model.addAttribute("activeFilteredCount", activeVendors.size());
                model.addAttribute("archivedFilteredCount", archivedVendors.size());
                model.addAttribute("vendorMenus", filteredMenus);
                model.addAttribute("importHistory", importLogService.recent(event, 5));
                model.addAttribute("auditLogEntries", auditLogService.recent(10));
                model.addAttribute("eventCompletedOrders", totalCompletedOrders);
                model.addAttribute("eventItemsSold", totalItemsSold);
                model.addAttribute("eventRevenue", totalRevenue);
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
            if (authority.getAuthority().equals("ROLE_" + role)){
                return true;
            }
        }
        return false;
    }

    private record VendorSalesSummary(Long vendorId,
                                      String vendorName,
                                      long totalOrders,
                                      long completedOrders,
                                      long itemsSold,
                                      BigDecimal revenue) { }
}
