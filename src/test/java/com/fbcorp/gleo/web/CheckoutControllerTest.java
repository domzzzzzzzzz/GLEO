package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.repo.TicketRepo;
import com.fbcorp.gleo.service.CheckoutService;
import com.fbcorp.gleo.service.AssetStorageService;
import com.fbcorp.gleo.service.QrDecoderService;
import com.fbcorp.gleo.service.TicketService;
import com.fbcorp.gleo.web.util.DeviceFingerprint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CheckoutController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AssetStorageService.class)
public class CheckoutControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TicketRepo ticketRepo;

    @MockBean
    TicketService ticketService;

    @MockBean
    QrDecoderService qrDecoderService;

    @MockBean
    CheckoutService checkoutService;

    @Test
    public void whenDeviceBoundToDifferentTicket_thenRedirectWithError() throws Exception {
        String eventCode = "EV1";
        MockMultipartFile file = new MockMultipartFile("qrFile", "qr.png", "image/png", "ignored".getBytes());

        // Build mock request values to compute expected device hash
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("User-Agent", "ua/1");
        req.setRemoteAddr("127.0.0.1");
        String deviceHash = DeviceFingerprint.from(req);

        Ticket boundTicket = new Ticket();
        boundTicket.setSerial("SN-001");
        boundTicket.setQrCode("OTHER");
        boundTicket.setBoundDeviceHash(deviceHash);

        when(qrDecoderService.decode(any())).thenReturn(Optional.of("DECODED"));
        when(ticketRepo.findFirstByEvent_CodeAndBoundDeviceHash(eq(eventCode), eq(deviceHash))).thenReturn(Optional.of(boundTicket));

        mockMvc.perform(multipart("/e/" + eventCode + "/ticket")
                .file(file)
                .header("User-Agent", "ua/1")
                .with(r -> {
                    r.setRemoteAddr("127.0.0.1");
                    return r;
                }))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/e/" + eventCode + "/ticket"))
                .andExpect(flash().attributeExists("toastError"));

        verify(ticketService, never()).validateAndBind(any(), any(), any());
    }

    @Test
    public void whenDeviceBoundToSameTicket_thenAllowed() throws Exception {
        String eventCode = "EV2";
        MockMultipartFile file = new MockMultipartFile("qrFile", "qr.png", "image/png", "ignored".getBytes());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("User-Agent", "ua/2");
        req.setRemoteAddr("10.0.0.1");
        String deviceHash = DeviceFingerprint.from(req);

        Ticket boundTicket = new Ticket();
        boundTicket.setSerial("SN-002");
        boundTicket.setQrCode("DECODED");
        boundTicket.setBoundDeviceHash(deviceHash);
        boundTicket.setId(42L);

        when(qrDecoderService.decode(any())).thenReturn(Optional.of("DECODED"));
        when(ticketRepo.findFirstByEvent_CodeAndBoundDeviceHash(eq(eventCode), eq(deviceHash))).thenReturn(Optional.of(boundTicket));
        when(ticketService.validateAndBind(eq(eventCode), eq("DECODED"), eq(deviceHash))).thenReturn(boundTicket);

        mockMvc.perform(multipart("/e/" + eventCode + "/ticket")
                .file(file)
                .header("User-Agent", "ua/2")
                .with(r -> {
                    r.setRemoteAddr("10.0.0.1");
                    return r;
                }))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/e/" + eventCode));

        verify(ticketService, times(1)).validateAndBind(eventCode, "DECODED", deviceHash);
    }

    @Test
    public void whenTicketUnbound_thenBindToDevice() throws Exception {
        String eventCode = "EV3";
        MockMultipartFile file = new MockMultipartFile("qrFile", "qr.png", "image/png", "ignored".getBytes());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("User-Agent", "ua/3");
        req.setRemoteAddr("192.168.0.1");
        String deviceHash = DeviceFingerprint.from(req);

        when(qrDecoderService.decode(any())).thenReturn(Optional.of("DECODED"));
        when(ticketRepo.findFirstByEvent_CodeAndBoundDeviceHash(eq(eventCode), eq(deviceHash))).thenReturn(Optional.empty());

        Ticket t = new Ticket();
        t.setId(100L);
        t.setQrCode("DECODED");
        t.setBoundDeviceHash(deviceHash);

        when(ticketService.validateAndBind(eq(eventCode), eq("DECODED"), eq(deviceHash))).thenReturn(t);

        mockMvc.perform(multipart("/e/" + eventCode + "/ticket")
                .file(file)
                .header("User-Agent", "ua/3")
                .with(r -> {
                    r.setRemoteAddr("192.168.0.1");
                    return r;
                }))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/e/" + eventCode));

        verify(ticketService, times(1)).validateAndBind(eventCode, "DECODED", deviceHash);
    }
}
