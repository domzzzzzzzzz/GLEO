package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.AuditLogEntry;
import com.fbcorp.gleo.repo.AuditLogRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepo auditLogRepo;

    public AuditLogService(AuditLogRepo auditLogRepo) {
        this.auditLogRepo = auditLogRepo;
    }

    @Transactional
    public void record(AuditLogEntry.Category category, String message, String username) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setCategory(category);
        entry.setMessage(message);
        entry.setUsername(username);
        entry.setCreatedAt(LocalDateTime.now());
        auditLogRepo.save(entry);
    }

    public List<AuditLogEntry> recent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return auditLogRepo.findByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    public List<AuditLogEntry> recentByCategory(AuditLogEntry.Category category, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return auditLogRepo.findByCategoryOrderByCreatedAtDesc(category, PageRequest.of(0, limit));
    }
}
