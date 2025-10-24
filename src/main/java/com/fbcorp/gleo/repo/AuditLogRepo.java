package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuditLogRepo extends JpaRepository<AuditLogEntry, Long> {
    List<AuditLogEntry> findByOrderByCreatedAtDesc(Pageable pageable);
    List<AuditLogEntry> findByCategoryOrderByCreatedAtDesc(AuditLogEntry.Category category, Pageable pageable);
}
