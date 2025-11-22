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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            TicketRepo ticketRepo) {
        this.checkoutService = checkoutService;
        this.ticketService = ticketService;
        this.qrDecoderService = qrDecoderService;
        this.ticketRepo = ticketRepo;
    }

    @GetMapping("/checkout")
    public String checkoutSummary(@PathVariable String eventCode,
            HttpSession session,
            Model model) {
        var activeTicket = resolveActiveTicket(eventCode, session);
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
            HttpSession session,
            Model model) {
        CartSession cart = (CartSession) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()) {
            return "redirect:/e/" + eventCode + "/cart";
        }

        Ticket ticket = resolveActiveTicket(eventCode, session).orElse(null);
        if (ticket == null) {
            return redirectToTicket(eventCode, "/e/" + eventCode + "/checkout");
        }

        Map<Long, List<CheckoutService.CartLine>> groups = new LinkedHashMap<>();
        cart.getAll().forEach((vendorId, itemsMap) -> {
            List<CheckoutService.CartLine> lines = new ArrayList<>();
            itemsMap.forEach((itemId, qty) -> lines.add(new CheckoutService.CartLine(itemId, qty)));
            groups.put(vendorId, lines);
        });

        var result = checkoutService.checkout(eventCode, ticket, groups);
        // remove accepted groups from cart
        result.orders.forEach(o -> cart.removeVendorGroup(o.getVendor().getId()));

        // Use PRG pattern: redirect to GET /checkout so refresh doesn't resubmit the
        // form
        // Summary page will load recent orders for this device via checkoutSummary()
        return "redirect:/e/" + eventCode + "/checkout";
    }

    @GetMapping("/ticket")
    public String ticketEntry(@PathVariable String eventCode,
            @RequestParam(value = "next", required = false) String next,
            @RequestParam(value = "message", required = false) String message,
            HttpSession session,
            Model model) {
        Optional<Ticket> activeTicket = resolveActiveTicket(eventCode, session);
        if (activeTicket.isPresent()) {
            return "redirect:" + sanitizeNext(eventCode, next);
        }

        return redirectToOverlay(eventCode, next, message);
    }

    @PostMapping("/ticket")
    public String linkTicketSession(@PathVariable String eventCode,
            @RequestParam(value = "qrFile", required = false) MultipartFile qrFile,
            @RequestParam(value = "next", required = false) String next,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        // FIRST: decode the QR payload so we can compare it against any ticket
        // already bound to this device. Decoding first allows the case where
        // the same ticket is re-uploaded from the same device.
        String decoded = qrDecoderService.decode(qrFile).orElse(null);
        if (!StringUtils.hasText(decoded)) {
            redirectAttributes.addFlashAttribute("toastError", "Please upload a clear QR code image.");
            return redirectToOverlay(eventCode, next);
        }

        // SECOND: Check if this device already has a ticket bound to it. If so,
        // ensure it's the same QR that was just uploaded; otherwise block the
        // upload and instruct the user to use the original QR or another device.
        String currentDeviceHash = DeviceFingerprint.from(request);
        Optional<Ticket> existingDeviceTicket = ticketRepo.findFirstByEvent_CodeAndBoundDeviceHash(eventCode,
                currentDeviceHash);

        if (existingDeviceTicket.isPresent()) {
            Ticket boundTicket = existingDeviceTicket.get();
            // Allow re-upload of the same ticket from the same device
            if (!boundTicket.getQrCode().equals(decoded)) {
                String serial = boundTicket.getSerial();
                boolean hasSerial = StringUtils.hasText(serial);
                String ticketLabel = hasSerial ? ("ticket " + serial) : "another ticket";
                redirectAttributes.addFlashAttribute("toastError",
                        "This device is already linked to " + ticketLabel + ". " +
                                "Please use your original QR code or use a different device.");
                return redirectToOverlay(eventCode, next);
            }
        }

        Ticket ticket;
        try {
            ticket = ticketService.validateAndBind(eventCode, decoded, currentDeviceHash);
        } catch (ResponseStatusException ex) {
            String message = ex.getReason() != null ? ex.getReason() : "Unable to link this ticket.";
            redirectAttributes.addFlashAttribute("toastError", message);
            return redirectToOverlay(eventCode, next);
        }

        session.setAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR, ticket.getId());
        session.setMaxInactiveInterval(86400 * 30);

        String redirectTarget = sanitizeNext(eventCode, next);
        return "redirect:" + redirectTarget;
    }

    private void populateSummary(Model model,
            String eventCode,
            List<Order> orders,
            Map<Long, String> rejected) {
        model.addAttribute("eventCode", eventCode);
        model.addAttribute("orders", orders);
        model.addAttribute("rejected", rejected);
        model.addAttribute("createdCount", orders.size());
        model.addAttribute("rejectedCount", rejected.size());
    }

    private Optional<Ticket> resolveActiveTicket(String eventCode, HttpSession session) {
        Object attr = session.getAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR);
        if (attr instanceof Long ticketId) {
            var ticketOpt = ticketService.findTicketByIdAndEvent(ticketId, eventCode);
            if (ticketOpt.isPresent()) {
                return ticketOpt;
            } else {
                session.removeAttribute(TicketSessionInterceptor.SESSION_TICKET_ATTR);
            }
        }
        return Optional.empty();
    }

    private String sanitizeNext(String eventCode, String next) {
        if (StringUtils.hasText(next) && next.startsWith("/e/" + eventCode)) {
            return next;
        }
        return "/e/" + eventCode;
    }

    private String redirectToTicket(String eventCode, String next) {
        return redirectToOverlay(eventCode, next,
                "Please scan your QR code to enter the event.");
    }

    private String redirectToOverlay(String eventCode, String next) {
        return redirectToOverlay(eventCode, next, null);
    }

    private String redirectToOverlay(String eventCode, String next, String message) {
        StringBuilder redirect = new StringBuilder("redirect:/e/").append(eventCode);
        List<String> params = new ArrayList<>();

        if (StringUtils.hasText(message)) {
            params.add("message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        }

        String sanitizedNext = sanitizeNext(eventCode, next);
        boolean hasCustomNext = StringUtils.hasText(next) && !sanitizedNext.equals("/e/" + eventCode);
        if (hasCustomNext) {
            params.add("next=" + URLEncoder.encode(sanitizedNext, StandardCharsets.UTF_8));
        }

        if (!params.isEmpty()) {
            redirect.append("?").append(String.join("&", params));
        }
        return redirect.toString();
    }
}
