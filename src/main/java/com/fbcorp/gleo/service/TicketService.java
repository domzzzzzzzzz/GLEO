package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.repo.TicketRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class TicketService {
    private final TicketRepo ticketRepo;
    private final EventPolicyService policyService;

    public TicketService(TicketRepo ticketRepo, EventPolicyService policyService) {
        this.ticketRepo = ticketRepo;
        this.policyService = policyService;
    }

    public Optional<Ticket> findTicketForDevice(String eventCode, String deviceHash){
        if (deviceHash == null || deviceHash.isBlank()) {
            return Optional.empty();
        }
        String normalized = sanitizeDeviceHash(deviceHash);
        return ticketRepo.findFirstByEvent_CodeAndBoundDeviceHash(eventCode, deviceHash)
                .or(() -> ticketRepo.findFirstByEvent_CodeAndBoundDeviceHash(eventCode, normalized));
    }

    public Optional<Ticket> findTicketByIdAndEvent(Long ticketId, String eventCode) {
        if (ticketId == null) {
            return Optional.empty();
        }
        return ticketRepo.findByIdAndEvent_Code(ticketId, eventCode);
    }

    public Ticket validateAndBind(String eventCode, String qrCode, String deviceHash){
        Ticket t = ticketRepo.findByQrCode(qrCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        Event e = policyService.get(eventCode);
        if (!t.getEvent().getId().equals(e.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ticket not for this event");
        }
        if (!t.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ticket inactive");
        }
        if (t.getBoundDeviceHash() == null) {
            t.setBoundDeviceHash(deviceHash);
            ticketRepo.save(t);
        } else if (!t.getBoundDeviceHash().equals(deviceHash)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ticket bound to another device");
        }
        return t;
    }

    public Ticket resolveTicket(String eventCode, String qrCode, String deviceHash){
        if (qrCode != null && !qrCode.isBlank()) {
            return validateAndBind(eventCode, qrCode.trim(), deviceHash);
        }
        return walkInTicket(eventCode, deviceHash);
    }

    private Ticket walkInTicket(String eventCode, String deviceHash){
        Event event = policyService.get(eventCode);
        String safeHash = sanitizeDeviceHash(deviceHash);
        String qrCode = ("WALKIN-" + event.getCode() + "-" + safeHash).toUpperCase(Locale.ROOT);
        return ticketRepo.findByQrCode(qrCode).orElseGet(() -> {
            Ticket t = new Ticket();
            t.setEvent(event);
            t.setQrCode(qrCode);
            t.setTierCode(TierCode.VIP);
            t.setHolderName("Walk-in Guest");
            t.setSerial(("WALKIN-" + safeHash).toUpperCase(Locale.ROOT));
            t.setActive(true);
            t.setBoundDeviceHash(safeHash);
            return ticketRepo.save(t);
        });
    }

    private String sanitizeDeviceHash(String deviceHash){
        String normalized = deviceHash != null ? deviceHash.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") : "";
        if (normalized.isEmpty()) {
            normalized = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        return normalized;
    }
    
    /**
     * Get ticket by QR code for authentication lookup
     */
    public Optional<Ticket> getTicketByQrCode(String qrCode) {
        if (qrCode == null || qrCode.isBlank()) {
            return Optional.empty();
        }
        return ticketRepo.findByQrCode(qrCode.trim());
    }

    public record QrValidationResult(boolean valid, String reason) {}

    /**
     * Validate a QR code for entry without binding it to a session yet.
     * @param eventCode Event code context
     * @param qrCode Raw QR payload
     * @param markUsed Whether to mark the ticket as checked-in when valid
     * @return validation result with failure reason (if any)
     */
    public QrValidationResult validateQrCodeForEntry(String eventCode, String qrCode, boolean markUsed) {
        if (!StringUtils.hasText(qrCode)) {
            return new QrValidationResult(false, "QR code is missing.");
        }

        Event event = policyService.get(eventCode);
        Optional<Ticket> ticketOpt = ticketRepo.findByQrCode(qrCode.trim());
        if (ticketOpt.isEmpty()) {
            return new QrValidationResult(false, "Ticket not found.");
        }

        Ticket ticket = ticketOpt.get();
        if (!ticket.getEvent().getId().equals(event.getId())) {
            return new QrValidationResult(false, "Ticket not valid for this event.");
        }
        if (!ticket.isActive()) {
            return new QrValidationResult(false, "Ticket is inactive.");
        }
        if (ticket.isCheckedIn()) {
            return new QrValidationResult(false, "Ticket already used.");
        }

        if (markUsed) {
            ticket.setCheckedIn(true);
            ticket.setCheckedInAt(LocalDateTime.now());
            ticketRepo.save(ticket);
        }

        return new QrValidationResult(true, null);
    }
}
