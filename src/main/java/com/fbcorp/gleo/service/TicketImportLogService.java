package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.TicketImportLog;
import com.fbcorp.gleo.domain.AuditLogEntry;
import com.fbcorp.gleo.repo.TicketImportLogRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketImportLogService {

    private final TicketImportLogRepo logRepo;
    private final AuditLogService auditLogService;

    public TicketImportLogService(TicketImportLogRepo logRepo, AuditLogService auditLogService) {
        this.logRepo = logRepo;
        this.auditLogService = auditLogService;
    }

    public void record(Event event, String username, TicketImportService.ImportResult result) {
        TicketImportLog log = new TicketImportLog();
        log.setEvent(event);
        log.setUsername(username);
        log.setTotalRows(result.total());
        log.setImportedRows(result.created());
        log.setDuplicateRows(result.duplicates());
        log.setInvalidRows(result.invalid());
        log.setErrorCount(result.errors() != null ? result.errors().size() : 0);
        if (result.errors() != null && !result.errors().isEmpty()) {
            log.setErrorSample(String.join(" | ", result.errors().size() > 3 ? result.errors().subList(0, 3) : result.errors()));
        }
        logRepo.save(log);
        auditLogService.record(AuditLogEntry.Category.TICKET,
                "Imported tickets: " + result.summaryMessage(),
                username);
    }

    public List<TicketImportLog> recent(Event event, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<TicketImportLog> logs = logRepo.findTop5ByEventOrderByCreatedAtDesc(event);
        return logs.size() > limit ? logs.subList(0, limit) : logs;
    }
}
