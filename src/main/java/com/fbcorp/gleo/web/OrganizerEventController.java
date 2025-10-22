package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.TicketRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.service.EventPolicyService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/organizer/events")
public class OrganizerEventController {

    private final VendorRepo vendorRepo;
    private final TicketRepo ticketRepo;
    private final EventPolicyService policyService;

    public OrganizerEventController(VendorRepo vendorRepo,
                                    TicketRepo ticketRepo,
                                    EventPolicyService policyService) {
        this.vendorRepo = vendorRepo;
        this.ticketRepo = ticketRepo;
        this.policyService = policyService;
    }

    @PostMapping("/{eventCode}/vendors")
    public String addVendor(@PathVariable String eventCode,
                            @RequestParam String name,
                            @RequestParam(required = false) String pin,
                            RedirectAttributes redirectAttributes){
        Event event = policyService.get(eventCode);
        Vendor vendor = new Vendor();
        vendor.setEvent(event);
        vendor.setName(name);
        vendor.setPinPlain(pin);
        vendorRepo.save(vendor);
        redirectAttributes.addFlashAttribute("toastMessage", "Vendor '" + name + "' added to event.");
        return "redirect:/dashboard";
    }

    @PostMapping("/{eventCode}/tickets/upload")
    public String uploadTickets(@PathVariable String eventCode,
                                @RequestParam("file") MultipartFile file,
                                RedirectAttributes redirectAttributes){
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError", "Please select a CSV file to upload.");
            return "redirect:/dashboard";
        }
        Event event = policyService.get(eventCode);
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))){
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                String qr = parts[0].trim();
                TierCode tier;
                try {
                    tier = TierCode.valueOf(parts[1].trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                String holder = parts[2].trim();
                String phone = parts[3].trim();

                if (ticketRepo.findByQrCode(qr).isPresent()) {
                    continue;
                }

                Ticket ticket = new Ticket();
                ticket.setEvent(event);
                ticket.setQrCode(qr);
                ticket.setTierCode(tier);
                ticket.setHolderName(holder);
                ticket.setHolderPhone(phone);
                if (parts.length > 4) {
                    ticket.setSerial(parts[4].trim());
                }
                ticketRepo.save(ticket);
                count++;
            }
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("toastError", "Failed to parse CSV: " + e.getMessage());
            return "redirect:/dashboard";
        }

        if (count > 0) {
            redirectAttributes.addFlashAttribute("toastMessage", count + " ticket(s) imported successfully.");
        } else {
            redirectAttributes.addFlashAttribute("toastError", "No tickets were imported. Check file formatting.");
        }
        return "redirect:/dashboard";
    }
}
