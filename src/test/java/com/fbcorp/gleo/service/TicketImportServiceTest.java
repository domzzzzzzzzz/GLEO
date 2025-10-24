package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.repo.TicketRepo;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TicketImportService.class)
class TicketImportServiceTest {

    @Autowired
    private TicketImportService ticketImportService;

    @Autowired
    private TicketRepo ticketRepo;

    @Autowired
    private EventRepo eventRepo;

    private Event event;

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setCode("EVT-1");
        event.setName("Test Event");
        eventRepo.save(event);
    }

    @Test
    void importsTicketsFromCsv() throws Exception {
        String csv = "QR,TIER,NAME,PHONE\nCSV-001,VIP,Guest One,0100\n";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tickets.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        TicketImportService.ImportResult result = ticketImportService.importSheet(event, file);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.duplicates()).isZero();
        assertThat(result.invalid()).isZero();
        assertThat(ticketRepo.findByQrCode("CSV-001")).isPresent();
    }

    @Test
    void importsTicketsFromExcelWorkbook() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Guests");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("QR");
            header.createCell(1).setCellValue("Tier");
            header.createCell(2).setCellValue("Name");
            header.createCell(3).setCellValue("Phone");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("XLSX-001");
            row.createCell(1).setCellValue("REG");
            row.createCell(2).setCellValue("Excel Guest");
            row.createCell(3).setCellValue("0111222333");

            workbook.write(out);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tickets.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray()
        );

        TicketImportService.ImportResult result = ticketImportService.importSheet(event, file);

        assertThat(result.created()).isEqualTo(1);
        assertThat(ticketRepo.findByQrCode("XLSX-001")).isPresent();
    }

    @Test
    void skipsDuplicatesAndCapturesErrors() throws Exception {
        Ticket existing = new Ticket();
        existing.setEvent(event);
        existing.setQrCode("DUP-001");
        existing.setTierCode(TierCode.VIP);
        existing.setHolderName("Existing Guest");
        existing.setActive(true);
        ticketRepo.save(existing);

        String csv = "QR,TIER,NAME,PHONE\nDUP-001,VIP,New Guest,0100\n,REG,Missing QR,0101\n";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "duplicates.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        TicketImportService.ImportResult result = ticketImportService.importSheet(event, file);

        assertThat(result.created()).isZero();
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.invalid()).isEqualTo(1);
        assertThat(result.errors()).isNotEmpty();
        assertThat(ticketRepo.findByQrCode("DUP-001")).hasValue(existing);
    }
}

