package com.fbcorp.gleo.config;

import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.repo.TicketRepo;
import com.fbcorp.gleo.service.TicketService;
import com.fbcorp.gleo.web.util.DeviceFingerprint;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
public class TicketSessionInterceptor implements HandlerInterceptor {

    public static final String SESSION_TICKET_ATTR = "ACTIVE_TICKET_ID";

    private final TicketService ticketService;
    private final TicketRepo ticketRepo;

    public TicketSessionInterceptor(TicketService ticketService, TicketRepo ticketRepo) {
        this.ticketService = ticketService;
        this.ticketRepo = ticketRepo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(true);
        String uri = request.getRequestURI();
        
        // Skip interceptor for ticket entry page itself to avoid redirect loop
        if (uri.endsWith("/ticket")) {
            return true;
        }
        
        String[] segments = uri.split("/");
        if (segments.length < 3) {
            return true;
        }
        String eventCode = segments[2];
        
        // Check if user is authenticated with Spring Security
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String qrCode = auth.getName();
            
            // Skip check for system accounts (admin, organizer, vendor, usher)
            if (qrCode.equals("admin") || qrCode.startsWith("organizer_") || 
                qrCode.startsWith("vendor_") || qrCode.startsWith("usher_")) {
                return true;
            }
            
            // Validate that the ticket is still bound to THIS device
            Ticket ticket = ticketRepo.findByQrCode(qrCode).orElse(null);
            if (ticket != null) {
                String currentDeviceHash = DeviceFingerprint.from(request);
                
                // If ticket is bound to a different device, logout and redirect to ticket entry
                if (ticket.getBoundDeviceHash() != null && !ticket.getBoundDeviceHash().equals(currentDeviceHash)) {
                    // Clear Spring Security authentication
                    SecurityContextHolder.clearContext();
                    session.invalidate();
                    
                    String redirect = "/e/" + eventCode + "/ticket?error=" + URLEncoder.encode("This QR code is linked to another device", StandardCharsets.UTF_8);
                    response.sendRedirect(redirect);
                    return false;
                }
                
                // User is authenticated AND device matches - allow access
                return true;
            }
        }
        
        // NEW: Check for persistent device-ticket cookie to restore authentication after server restart
        String currentDeviceHash = DeviceFingerprint.from(request);
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String cookieName = "gleo_device_" + eventCode;
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    try {
                        // Decode the cookie value: Base64(deviceHash:qrCode)
                        String decoded = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
                        String[] parts = decoded.split(":", 2);
                        if (parts.length == 2) {
                            String savedDeviceHash = parts[0];
                            String savedQrCode = parts[1];
                            
                            // Verify device matches (prevent cookie theft)
                            if (savedDeviceHash.equals(currentDeviceHash)) {
                                // Verify ticket is still valid
                                Ticket ticket = ticketRepo.findByQrCode(savedQrCode).orElse(null);
                                if (ticket != null && ticket.isActive() && 
                                    ticket.getEvent().getCode().equals(eventCode)) {
                                    
                                    // Restore Spring Security authentication
                                    UsernamePasswordAuthenticationToken authentication = 
                                        new UsernamePasswordAuthenticationToken(savedQrCode, null, List.of());
                                    SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                                    securityContext.setAuthentication(authentication);
                                    SecurityContextHolder.setContext(securityContext);
                                    
                                    // Save to session so it persists across requests
                                    session.setAttribute(
                                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, 
                                        securityContext
                                    );
                                    
                                    // User successfully auto-authenticated from cookie
                                    return true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Invalid cookie format - ignore and continue to normal flow
                    }
                    break;
                }
            }
        }
        
        // Old logic for session-based tickets
        if (session.getAttribute(SESSION_TICKET_ATTR) != null) {
            return true;
        }
        
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
