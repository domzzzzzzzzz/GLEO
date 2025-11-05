package com.fbcorp.gleo.web;

import com.fbcorp.gleo.service.AuditLogService;
import com.fbcorp.gleo.service.AdminPreferenceService;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("@permissionService.isAdmin(authentication)")
public class AdminLogsController {

    private final AuditLogService auditLogService;
    private final AdminPreferenceService adminPreferenceService;

    public AdminLogsController(AuditLogService auditLogService, AdminPreferenceService adminPreferenceService) {
        this.auditLogService = auditLogService;
        this.adminPreferenceService = adminPreferenceService;
    }

    @GetMapping("/logs")
    public String logs(Model model) {
        // Use a reasonable default limit for the quick viewer. For full paging, extend service.
        int limit = 200;
        var logs = auditLogService.recent(limit);
        model.addAttribute("logs", logs);
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
                        // ignore parse errors
                    }
                }
            });
        }
        return "admin/logs";
    }
}
