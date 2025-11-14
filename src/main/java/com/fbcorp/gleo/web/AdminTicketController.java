package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.repo.TicketRepo;
import com.fbcorp.gleo.service.EventPolicyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/events/{eventCode}/tickets")
@PreAuthorize("@permissionService.isAdmin(authentication)")
public class AdminTicketController {

    private final EventPolicyService policyService;
    private final TicketRepo ticketRepo;

    public AdminTicketController(EventPolicyService policyService,
                                 TicketRepo ticketRepo) {
        this.policyService = policyService;
        this.ticketRepo = ticketRepo;
    }

    @GetMapping("/new")
    public String newTicketForm(@PathVariable String eventCode, Model model) {
        Event event = policyService.get(eventCode);
        model.addAttribute("event", event);
        model.addAttribute("tiers", TierCode.values());
        return "admin/ticket_upload";
    }

    @PostMapping
    public String createTickets(@PathVariable String eventCode,
                                @RequestParam("qrCodes") String qrCodes,
                                @RequestParam(value = "tier", defaultValue = "REG") TierCode tierCode,
                                @RequestParam(value = "holderName", required = false) String holderName,
                                RedirectAttributes redirectAttributes) {
        Event event = policyService.get(eventCode);
        int created = 0;
        int skipped = 0;
        for (String raw : qrCodes.split("\\r?\\n")) {
            String trimmed = raw != null ? raw.trim() : "";
            if (!StringUtils.hasText(trimmed)) continue;
            if (ticketRepo.findByQrCode(trimmed).isPresent()) {
                skipped++;
                continue;
            }
            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setQrCode(trimmed);
            ticket.setTierCode(tierCode);
            ticket.setHolderName(StringUtils.hasText(holderName) ? holderName.trim() : null);
            ticket.setActive(true);
            ticketRepo.save(ticket);
            created++;
        }
        redirectAttributes.addFlashAttribute("toastMessage",
                "Tickets created: " + created + (skipped > 0 ? " · Duplicates skipped: " + skipped : ""));
        return "redirect:/admin/events/" + eventCode + "/tickets/new";
    }
}
