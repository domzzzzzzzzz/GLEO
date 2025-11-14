package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepo extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByQrCode(String qrCode);

    Optional<Ticket> findByEvent_CodeAndBoundDeviceHash(String eventCode, String boundDeviceHash);

    Optional<Ticket> findByIdAndEvent_Code(Long id, String eventCode);

    List<Ticket> findByEvent(Event event);
}
