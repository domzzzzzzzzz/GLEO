package com.fbcorp.gleo.config;

import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.repo.TicketRepo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class TicketSessionInterceptor implements HandlerInterceptor {

    public static final String SESSION_TICKET_ATTR = "ACTIVE_TICKET_ID";
    private static final String REDIRECT_MESSAGE = "Please scan your QR code to enter the event.";
    private static final String DEFAULT_EVENT_CODE = "G2025";

    private final TicketRepo ticketRepo;

    public TicketSessionInterceptor(TicketRepo ticketRepo) {
        this.ticketRepo = ticketRepo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/e/")) {
            return true;
        }

        String[] segments = uri.split("/");
        if (segments.length < 3) {
            return true;
        }
        String eventCode = segments[2];

        // Force all event routes to the default event code
        if (!DEFAULT_EVENT_CODE.equals(eventCode)) {
            String newUri = uri.replaceFirst("/e/[^/]+", "/e/" + DEFAULT_EVENT_CODE);
            if (request.getQueryString() != null) {
                newUri = newUri + "?" + request.getQueryString();
            }
            response.sendRedirect(newUri);
            return false;
        }

        String eventRoot = "/e/" + eventCode;

        // Allow landing page to render so the inline overlay can prompt for ticket upload
        if (isEventHomeRequest(uri, eventRoot) && "GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Allow access to ticket upload/validation endpoints
        if (uri.startsWith(eventRoot + "/ticket")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            Object attr = session.getAttribute(SESSION_TICKET_ATTR);
            if (attr instanceof Long ticketId) {
                Ticket ticket = ticketRepo.findById(ticketId).orElse(null);
                if (ticket != null && ticket.isActive() && ticket.getEvent().getCode().equals(eventCode)) {
                    return true;
                }
                session.removeAttribute(SESSION_TICKET_ATTR);
            }
        }

        String next = uri;
        if (request.getQueryString() != null) {
            next = next + "?" + request.getQueryString();
        }
        String redirect = new StringBuilder(eventRoot)
                .append("?message=")
                .append(URLEncoder.encode(REDIRECT_MESSAGE, StandardCharsets.UTF_8))
                .append("&next=")
                .append(URLEncoder.encode(next, StandardCharsets.UTF_8))
                .toString();
        response.sendRedirect(redirect);
        return false;
    }

    private boolean isEventHomeRequest(String uri, String eventRoot) {
        return uri.equals(eventRoot) || uri.equals(eventRoot + "/");
    }
}
