package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.AuditLogEntry;
import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.UserAccountRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.service.AssetStorageService;
import com.fbcorp.gleo.service.AuditLogService;
import com.fbcorp.gleo.service.EventPolicyService;
import com.fbcorp.gleo.service.OrganizerAnalyticsService;
import com.fbcorp.gleo.service.TicketImportLogService;
import com.fbcorp.gleo.service.TicketImportService;
import com.fbcorp.gleo.config.StaticResourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OrganizerEventController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = StaticResourceConfig.class)
)
class OrganizerEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VendorRepo vendorRepo;
    @MockBean
    private MenuItemRepo menuItemRepo;
    @MockBean
    private EventPolicyService policyService;
    @MockBean
    private TicketImportService ticketImportService;
    @MockBean
    private AssetStorageService assetStorageService;
    @MockBean
    private TicketImportLogService importLogService;
    @MockBean
    private OrganizerAnalyticsService analyticsService;
    @MockBean
    private AuditLogService auditLogService;
    @MockBean
    private UserAccountRepo userAccountRepo;

    @BeforeEach
    void setupMocks() {
        when(assetStorageService.getRootDir()).thenReturn(Path.of("uploads").toAbsolutePath());
    }

    @Test
    @DisplayName("Exporting vendors generates CSV and records an audit entry")
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void exportVendorsRecordsAuditLog() throws Exception {
        Event event = new Event();
        event.setId(1L);
        event.setCode("EVT");
        event.setName("Demo Event");

        Vendor vendor = new Vendor();
        vendor.setId(10L);
        vendor.setName("Basbosa");
        vendor.setEvent(event);

        when(policyService.get("EVT")).thenReturn(event);
        when(vendorRepo.findByEvent(event)).thenReturn(List.of(vendor));
        OrganizerAnalyticsService.VendorStats stats =
                new OrganizerAnalyticsService.VendorStats(vendor, 2, 1, 5, new BigDecimal("150.00"));
        when(analyticsService.computeVendorStats(event, List.of(vendor)))
                .thenReturn(Map.of(vendor.getId(), stats));

        mockMvc.perform(get("/organizer/events/{code}/vendors/export", "EVT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("vendor_roster.csv")));

        verify(auditLogService).record(
                eq(AuditLogEntry.Category.VENDOR),
                contains("Exported vendor roster"),
                eq("organizer"));
    }

    @Test
    @DisplayName("Editing a vendor logs specific changes")
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void editVendorIncludesChangeDetails() throws Exception {
        Event event = new Event();
        event.setId(2L);
        event.setCode("EVT");
        event.setName("Demo Event");

        Vendor vendor = new Vendor();
        vendor.setId(20L);
        vendor.setName("Old Name");
        vendor.setEvent(event);

        when(policyService.get("EVT")).thenReturn(event);
        when(vendorRepo.findById(20L)).thenReturn(Optional.of(vendor));

        mockMvc.perform(post("/organizer/events/{code}/vendors/{id}/edit", "EVT", 20L)
                        .with(csrf())
                        .param("name", "New Name")
                        .param("pin", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        verify(vendorRepo).save(vendor);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(
                eq(AuditLogEntry.Category.VENDOR),
                messageCaptor.capture(),
                eq("organizer"));

        String message = messageCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(message)
                .contains("Updated vendor 'New Name'")
                .contains("renamed")
                .contains("added pickup PIN");
    }
}
