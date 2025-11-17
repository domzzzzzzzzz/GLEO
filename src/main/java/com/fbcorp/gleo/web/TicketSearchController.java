package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.repo.TicketRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/tickets")
@PreAuthorize("@permissionService.isAdmin(authentication)")
public class TicketSearchController {

    private final TicketRepo ticketRepo;
    private final EventRepo eventRepo;

    public TicketSearchController(TicketRepo ticketRepo, EventRepo eventRepo) {
        this.ticketRepo = ticketRepo;
        this.eventRepo = eventRepo;
    }

    @GetMapping("/search")
    public ResponseEntity<TicketDTO> searchTicket(
            @RequestParam("q") String query,
            @RequestParam("eventCode") String eventCode) {
        
        var event = eventRepo.findByCode(eventCode)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        // Try to find by QR code first
        Optional<Ticket> ticket = ticketRepo.findByQrCode(query);
        
        // If not found, try to parse as ID
        if (ticket.isEmpty()) {
            try {
                Long id = Long.parseLong(query);
                ticket = ticketRepo.findById(id);
            } catch (NumberFormatException ignored) {
                // Not a valid ID, continue
            }
        }

        // Check if ticket belongs to this event
        if (ticket.isPresent() && ticket.get().getEvent().getId().equals(event.getId())) {
            Ticket t = ticket.get();
            TicketDTO dto = new TicketDTO(
                    t.getId(),
                    t.getQrCode(),
                    t.getHolderName(),
                    t.getTierCode() != null ? t.getTierCode().name() : "REG",
                    t.isActive()
            );
            return ResponseEntity.ok(dto);
        }

        return ResponseEntity.notFound().build();
    }

    private record TicketDTO(
            Long id,
            String qrCode,
            String holderName,
            String tierCode,
            boolean active
    ) {}
}
