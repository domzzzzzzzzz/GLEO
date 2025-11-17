package com.fbcorp.gleo.web;

import com.fbcorp.gleo.config.TicketSessionInterceptor;
import com.fbcorp.gleo.domain.Order;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.repo.TicketRepo;
import com.fbcorp.gleo.service.CheckoutService;
import com.fbcorp.gleo.service.QrDecoderService;
import com.fbcorp.gleo.service.TicketService;
import com.fbcorp.gleo.web.util.DeviceFingerprint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/e/{eventCode}")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final TicketService ticketService;
    private final QrDecoderService qrDecoderService;
    private final TicketRepo ticketRepo;

    public CheckoutController(CheckoutService checkoutService,
                              TicketService ticketService,
                              QrDecoderService qrDecoderService,
                              TicketRepo ticketRepo){
        this.checkoutService = checkoutService;
        this.ticketService = ticketService;
        this.qrDecoderService = qrDecoderService;
        this.ticketRepo = ticketRepo;
    }

    @GetMapping("/checkout")
    public String checkoutSummary(@PathVariable String eventCode,
                                  HttpServletRequest request,
                                  HttpSession session,
                                  Model model){
        var activeTicket = resolveActiveTicket(eventCode, session, request);
        activeTicket.ifPresent(ticket -> model.addAttribute("activeTicket", ticket));

        // Only show orders for the current ticket holder (not old device orders)
        List<Order> orders = activeTicket
                .map(checkoutService::recentOrdersForTicket)
                .orElseGet(List::of); // Empty list if no active ticket

        populateSummary(model, eventCode, orders, Collections.emptyMap());
        return "checkout_result";
    }

    @PostMapping("/checkout")
    public String checkout(@PathVariable String eventCode,
                           @RequestParam(name = "qr", required = false) String qr,
                           HttpServletRequest request,
                           HttpSession session,
                           Model model){
        CartSession cart = (CartSession) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()){
            return "redirect:/e/" + eventCode + "/cart";
        }

        Map<Long, List<CheckoutService.CartLine>> groups = new LinkedHashMap<>();
        cart.getAll().forEach((vendorId, itemsMap) -> {
            List<CheckoutService.CartLine> lines = new ArrayList<>();
            itemsMap.forEach((itemId, qty) -> lines.add(new CheckoutService.CartLine(itemId, qty)));
            groups.put(vendorId, lines);
        });

        var normalizedQr = (qr != null && !qr.isBlank()) ? qr.trim() : null;
        var result = checkoutService.checkout(eventCode, normalizedQr, DeviceFingerprint.from(request), groups);
        if (result.getTicket() != null) {
            session.setAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR, result.getTicket().getId());
        }
        // remove accepted groups from cart
        result.orders.forEach(o -> cart.removeVendorGroup(o.getVendor().getId()));

        // Use PRG pattern: redirect to GET /checkout so refresh doesn't resubmit the form
        // Summary page will load recent orders for this device via checkoutSummary()
        return "redirect:/e/" + eventCode + "/checkout";
    }

    @GetMapping("/ticket")
    public String ticketEntry(@PathVariable String eventCode,
                              @RequestParam(value = "next", required = false) String next,
                              HttpServletRequest request,
                              HttpSession session,
                              Model model) {
        // Check if this device already has a ticket bound to it for this event
        String currentDeviceHash = DeviceFingerprint.from(request);
        Optional<Ticket> existingTicket = ticketRepo.findFirstByEvent_CodeAndBoundDeviceHash(eventCode, currentDeviceHash);
        
        if (existingTicket.isPresent() && existingTicket.get().isActive()) {
            // Device already has a ticket! Auto-login instead of showing upload form
            Ticket ticket = existingTicket.get();
            
            // Restore Spring Security authentication
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(ticket.getQrCode(), null, List.of());
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            
            // Save to session
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, 
                securityContext
            );
            
            // Redirect to target page
            String redirectTarget = sanitizeNext(eventCode, next);
            return "redirect:" + redirectTarget;
        }
        
        model.addAttribute("eventCode", eventCode);
        model.addAttribute("next", sanitizeNext(eventCode, next));
        return "ticket_entry";
    }

    @PostMapping("/ticket")
    public String linkTicketSession(@PathVariable String eventCode,
                                    @RequestParam(value = "qrFile", required = false) MultipartFile qrFile,
                                    @RequestParam(value = "next", required = false) String next,
                                    HttpServletRequest request,
                                    jakarta.servlet.http.HttpServletResponse response,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        // FIRST: Check if this device already has a ticket bound to it
        String currentDeviceHash = DeviceFingerprint.from(request);
        Optional<Ticket> existingDeviceTicket = ticketRepo.findFirstByEvent_CodeAndBoundDeviceHash(eventCode, currentDeviceHash);
        
        if (existingDeviceTicket.isPresent()) {
            // Device already has a ticket! Don't allow uploading a different QR code
            Ticket boundTicket = existingDeviceTicket.get();
            redirectAttributes.addFlashAttribute("toastError", 
                "This device is already linked to a ticket (" + boundTicket.getSerial() + "). " +
                "Please use your original QR code or use a different device.");
            return "redirect:/e/" + eventCode + "/ticket";
        }
        
        String decoded = qrDecoderService.decode(qrFile).orElse(null);
        if (!StringUtils.hasText(decoded)) {
            redirectAttributes.addFlashAttribute("toastError", "Please upload a clear QR code image.");
            return "redirect:/e/" + eventCode + "/ticket";
        }
        
        // Find ticket by QR code
        Ticket ticket = ticketRepo.findByQrCode(decoded).orElse(null);
        if (ticket == null) {
            redirectAttributes.addFlashAttribute("toastError", "Invalid QR code. Ticket not found.");
            return "redirect:/e/" + eventCode + "/ticket";
        }
        
        // Verify ticket belongs to this event
        if (!ticket.getEvent().getCode().equals(eventCode)) {
            redirectAttributes.addFlashAttribute("toastError", "This ticket is not valid for this event.");
            return "redirect:/e/" + eventCode + "/ticket";
        }
        
        // Check if ticket is active
        if (!ticket.isActive()) {
            redirectAttributes.addFlashAttribute("toastError", "This ticket has been deactivated.");
            return "redirect:/e/" + eventCode + "/ticket";
        }
        
        // Check if ticket is already bound to a different device (reuse currentDeviceHash from above)
        if (ticket.getBoundDeviceHash() != null && !ticket.getBoundDeviceHash().equals(currentDeviceHash)) {
            redirectAttributes.addFlashAttribute("toastError", "This QR code is already linked to another device. Please use your QR code on your original device.");
            return "redirect:/e/" + eventCode + "/ticket";
        }
        
        // Bind ticket to this device if not already bound
        if (ticket.getBoundDeviceHash() == null) {
            ticket.setBoundDeviceHash(currentDeviceHash);
            ticketRepo.save(ticket);
        }
        
        // Perform Spring Security authentication with the ticket's QR code as username
        UsernamePasswordAuthenticationToken authentication = 
            new UsernamePasswordAuthenticationToken(ticket.getQrCode(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // Save authentication to session
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, 
            SecurityContextHolder.getContext());
        
        // Make session persistent for 30 days
        session.setMaxInactiveInterval(86400 * 30); // 30 days in seconds
        
        // Create persistent cookie to remember this device-ticket binding
        jakarta.servlet.http.Cookie deviceTicketCookie = new jakarta.servlet.http.Cookie(
            "gleo_device_" + eventCode, 
            java.util.Base64.getEncoder().encodeToString((currentDeviceHash + ":" + ticket.getQrCode()).getBytes())
        );
        deviceTicketCookie.setMaxAge(86400 * 30); // 30 days
        deviceTicketCookie.setPath("/");
        deviceTicketCookie.setHttpOnly(true);
        response.addCookie(deviceTicketCookie);
        
        String redirectTarget = sanitizeNext(eventCode, next);
        return "redirect:" + redirectTarget;
    }

    private void populateSummary(Model model,
                                 String eventCode,
                                 List<Order> orders,
                                 Map<Long, String> rejected){
        model.addAttribute("eventCode", eventCode);
        model.addAttribute("orders", orders);
        model.addAttribute("rejected", rejected);
        model.addAttribute("createdCount", orders.size());
        model.addAttribute("rejectedCount", rejected.size());
    }

    private Optional<Ticket> resolveActiveTicket(String eventCode, HttpSession session, HttpServletRequest request) {
        Object attr = session.getAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR);
        if (attr instanceof Long ticketId) {
            var ticketOpt = ticketService.findTicketByIdAndEvent(ticketId, eventCode);
            if (ticketOpt.isPresent()) {
                return ticketOpt;
            } else {
                session.removeAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR);
            }
        }
        return ticketService.findTicketForDevice(eventCode, DeviceFingerprint.from(request));
    }

    private String sanitizeNext(String eventCode, String next) {
        if (StringUtils.hasText(next) && next.startsWith("/e/" + eventCode)) {
            return next;
        }
        return "/e/" + eventCode;
    }
}
