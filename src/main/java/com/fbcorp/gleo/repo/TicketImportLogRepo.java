package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.TicketImportLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketImportLogRepo extends JpaRepository<TicketImportLog, Long> {
    List<TicketImportLog> findTop5ByEventOrderByCreatedAtDesc(Event event);
}

