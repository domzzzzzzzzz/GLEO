package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.repo.TicketRepo;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class TicketImportService {

    public record ImportResult(int created, int duplicates, int invalid, int total, List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public String summaryMessage() {
            StringBuilder sb = new StringBuilder();
            sb.append(total).append(" row(s) processed. ");
            sb.append(created).append(" imported");
            if (duplicates > 0) {
                sb.append(", ").append(duplicates).append(" duplicate");
                if (duplicates != 1) {
                    sb.append('s');
                }
                sb.append(" skipped");
            }
            if (invalid > 0) {
                sb.append(", ").append(invalid).append(" invalid");
            }
            return sb.toString();
        }
    }

    private static final int MAX_ERROR_MESSAGES = 10;

    private final TicketRepo ticketRepo;

    public TicketImportService(TicketRepo ticketRepo) {
        this.ticketRepo = ticketRepo;
    }

    public byte[] generateCsvTemplate() {
        String template = """
                # QR,TIER,Holder name,Phone,Serial
                SAMPLE-001,VIP,Guest Name,+201000000000,SAMPLE-1
                """;
        return template.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public ImportResult importSheet(Event event, MultipartFile file) throws IOException {
        List<RowValues> rows = extractRows(file);
        if (rows.isEmpty()) {
            return new ImportResult(0, 0, 0, 0, List.of("The uploaded file does not contain any data rows."));
        }

        Set<String> existingQrCodes = ticketRepo.findByEvent(event).stream()
                .map(ticket -> ticket.getQrCode() != null ? ticket.getQrCode().trim() : "")
                .filter(code -> !code.isBlank())
                .collect(java.util.stream.Collectors.toSet());

        Set<String> seenInUpload = new HashSet<>();
        List<Ticket> toPersist = new ArrayList<>();
        int duplicates = 0;
        int invalid = 0;
        List<String> errors = new ArrayList<>();

        for (RowValues row : rows) {
            String qr = trimToNull(row.qrCode());
            if (qr == null) {
                invalid++;
                appendError(errors, row.index(), "Missing QR code.");
                continue;
            }
            if (!seenInUpload.add(qr)) {
                duplicates++;
                appendError(errors, row.index(), "Duplicate QR code inside file: " + qr);
                continue;
            }
            if (existingQrCodes.contains(qr)) {
                duplicates++;
                continue;
            }

            TierCode tierCode;
            try {
                tierCode = parseTier(row.tier());
            } catch (IllegalArgumentException ex) {
                invalid++;
                appendError(errors, row.index(), "Unknown tier '" + row.tier() + "'. Expected one of " + java.util.Arrays.toString(TierCode.values()));
                continue;
            }

            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setQrCode(qr);
            ticket.setTierCode(tierCode);
            ticket.setHolderName(trimToNull(row.holderName()));
            ticket.setHolderPhone(trimToNull(row.phone()));
            ticket.setSerial(trimToNull(row.serial()));
            ticket.setActive(true);

            toPersist.add(ticket);
            existingQrCodes.add(qr);
        }

        if (!toPersist.isEmpty()) {
            ticketRepo.saveAll(toPersist);
        }

        return new ImportResult(toPersist.size(), duplicates, invalid, rows.size(), errors);
    }

    private TierCode parseTier(String tierRaw) {
        String tier = trimToNull(tierRaw);
        if (tier == null) {
            throw new IllegalArgumentException("Tier missing");
        }
        return TierCode.valueOf(tier.toUpperCase(Locale.ROOT));
    }

    private void appendError(List<String> errors, int row, String message) {
        if (errors.size() >= MAX_ERROR_MESSAGES) {
            return;
        }
        errors.add("Row " + row + ": " + message);
    }

    private List<RowValues> extractRows(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String lowercase = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        if (lowercase.endsWith(".xlsx") || lowercase.endsWith(".xls")) {
            return extractFromWorkbook(file.getInputStream());
        }
        return extractFromCsv(file.getInputStream());
    }

    private List<RowValues> extractFromCsv(InputStream inputStream) throws IOException {
        List<RowValues> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                index++;
                if (line.isBlank()) {
                    continue;
                }
                if (line.trim().startsWith("#")) {
                    continue;
                }
                List<String> cells = splitCsvLine(line);
                if (isHeaderRow(cells)) {
                    continue;
                }
                rows.add(toRowValues(index, cells));
            }
        }
        return rows;
    }

    private List<RowValues> extractFromWorkbook(InputStream inputStream) throws IOException {
        List<RowValues> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                return rows;
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int index = 0;
            for (Row row : sheet) {
                index++;
                List<String> cells = new ArrayList<>();
                int cellCount = Math.max(5, row.getLastCellNum());
                for (int i = 0; i < cellCount; i++) {
                    var cell = row.getCell(i);
                    String value = cell != null ? formatter.formatCellValue(cell) : "";
                    cells.add(value);
                }
                if (cells.stream().allMatch(String::isBlank)) {
                    continue;
                }
                if (isHeaderRow(cells)) {
                    continue;
                }
                rows.add(toRowValues(index, cells));
            }
        }
        return rows;
    }

    private boolean isHeaderRow(List<String> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        String first = cells.get(0);
        if (first == null) {
            return false;
        }
        String normalized = first.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (normalized.equals("qr") || normalized.equals("qrcode")) {
            return true;
        }
        if (cells.size() > 1) {
            String second = cells.get(1);
            if (second != null) {
                String secondNorm = second.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
                return secondNorm.equals("tier") || secondNorm.equals("tiercode");
            }
        }
        return false;
    }

    private RowValues toRowValues(int index, List<String> cells) {
        String qr = getCell(cells, 0);
        String tier = getCell(cells, 1);
        String name = getCell(cells, 2);
        String phone = getCell(cells, 3);
        String serial = getCell(cells, 4);
        return new RowValues(index, qr, tier, name, phone, serial);
    }

    private String getCell(List<String> cells, int idx) {
        if (idx < 0 || idx >= cells.size()) {
            return "";
        }
        return Objects.toString(cells.get(idx), "");
    }

    private List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (c == ',' && !insideQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());

        return result.stream().map(String::trim).toList();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record RowValues(int index, String qrCode, String tier, String holderName, String phone, String serial) {}
}
