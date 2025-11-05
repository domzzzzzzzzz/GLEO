package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.AdminPreference;
import com.fbcorp.gleo.domain.UserAccount;
import com.fbcorp.gleo.repo.AdminPreferenceRepo;
import com.fbcorp.gleo.repo.UserAccountRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AdminPreferenceService {

    private final AdminPreferenceRepo repo;
    private final UserAccountRepo userAccountRepo;

    public AdminPreferenceService(AdminPreferenceRepo repo, UserAccountRepo userAccountRepo) {
        this.repo = repo;
        this.userAccountRepo = userAccountRepo;
    }

    public Optional<AdminPreference> findByUsername(String username) {
        return repo.findByUser_Username(username);
    }

    @Transactional
    public AdminPreference saveForUsername(String username, String theme, String menuOrderJson) {
        UserAccount user = userAccountRepo.findByUsername(username).orElse(null);
        if (user == null) throw new IllegalArgumentException("user not found: " + username);
        AdminPreference pref = repo.findByUser(user).orElseGet(() -> {
            AdminPreference p = new AdminPreference();
            p.setUser(user);
            return p;
        });
        if (theme != null) pref.setTheme(theme);
        if (menuOrderJson != null) pref.setMenuOrderJson(menuOrderJson);
        return repo.save(pref);
    }
}
