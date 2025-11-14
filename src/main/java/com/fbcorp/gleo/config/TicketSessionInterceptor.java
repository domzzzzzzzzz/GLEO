package com.fbcorp.gleo.config;

import com.fbcorp.gleo.service.TicketService;
import com.fbcorp.gleo.web.util.DeviceFingerprint;
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

    private final TicketService ticketService;

    public TicketSessionInterceptor(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(true);
        if (session.getAttribute(SESSION_TICKET_ATTR) != null) {
            return true;
        }
        String uri = request.getRequestURI();
        String[] segments = uri.split("/");
        if (segments.length < 3) {
            return true;
        }
        String eventCode = segments[2];
        ticketService.findTicketForDevice(eventCode, DeviceFingerprint.from(request))
                .ifPresent(ticket -> session.setAttribute(SESSION_TICKET_ATTR, ticket.getId()));
        if (session.getAttribute(SESSION_TICKET_ATTR) != null) {
            return true;
        }
        String next = uri;
        if (request.getQueryString() != null) {
            next = next + "?" + request.getQueryString();
        }
        String redirect = "/e/" + eventCode + "/ticket?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8);
        response.sendRedirect(redirect);
        return false;
    }
}
