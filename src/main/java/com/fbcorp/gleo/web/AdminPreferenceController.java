package com.fbcorp.gleo.web;

import com.fbcorp.gleo.service.AdminPreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/api/preferences")
public class AdminPreferenceController {

    private final AdminPreferenceService prefService;

    public AdminPreferenceController(AdminPreferenceService prefService) {
        this.prefService = prefService;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<?> savePreferences(@RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return ResponseEntity.status(401).build();
        String username = auth.getName();
        String theme = body.get("theme");
        String menuOrder = body.get("menuOrderJson");
        try {
            prefService.saveForUsername(username, theme, menuOrder);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
