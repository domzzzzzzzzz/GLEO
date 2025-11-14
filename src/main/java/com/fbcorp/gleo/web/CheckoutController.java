package com.fbcorp.gleo.web;

import com.fbcorp.gleo.config.TicketSessionInterceptor;
import com.fbcorp.gleo.domain.Order;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.service.CheckoutService;
import com.fbcorp.gleo.service.QrDecoderService;
import com.fbcorp.gleo.service.TicketService;
import com.fbcorp.gleo.web.util.DeviceFingerprint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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

    public CheckoutController(CheckoutService checkoutService,
                              TicketService ticketService,
                              QrDecoderService qrDecoderService){
        this.checkoutService = checkoutService;
        this.ticketService = ticketService;
        this.qrDecoderService = qrDecoderService;
    }

    @GetMapping("/checkout")
    public String checkoutSummary(@PathVariable String eventCode,
                                  HttpServletRequest request,
                                  HttpSession session,
                                  Model model){
        var activeTicket = resolveActiveTicket(eventCode, session, request);
        activeTicket.ifPresent(ticket -> model.addAttribute("activeTicket", ticket));

        String fingerprint = DeviceFingerprint.from(request);
        List<Order> orders = activeTicket
                .map(checkoutService::recentOrdersForTicket)
                .orElseGet(() -> checkoutService.recentOrdersForDevice(eventCode, fingerprint));

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
                              Model model) {
        model.addAttribute("eventCode", eventCode);
        model.addAttribute("next", sanitizeNext(eventCode, next));
        return "ticket_entry";
    }

    @PostMapping("/ticket")
    public String linkTicketSession(@PathVariable String eventCode,
                                    @RequestParam(value = "qrFile", required = false) MultipartFile qrFile,
                                    @RequestParam(value = "next", required = false) String next,
                                    HttpServletRequest request,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        String decoded = qrDecoderService.decode(qrFile).orElse(null);
        if (!StringUtils.hasText(decoded)) {
            redirectAttributes.addFlashAttribute("toastError", "Please upload a clear QR code image.");
            return "redirect:/e/" + eventCode + "/ticket";
        }
        String redirectTarget = sanitizeNext(eventCode, next);
        try {
            Ticket ticket = ticketService.validateAndBind(eventCode, decoded, DeviceFingerprint.from(request));
            session.setAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR, ticket.getId());
            return "redirect:" + redirectTarget;
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("toastError", ex.getReason());
            return "redirect:/e/" + eventCode + "/ticket";
        }
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
        return "/e/" + eventCode + "/cart";
    }
}
