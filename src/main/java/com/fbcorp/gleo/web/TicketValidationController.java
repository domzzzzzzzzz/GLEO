package com.fbcorp.gleo.web;

import com.fbcorp.gleo.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/e/{eventCode}")
public class TicketValidationController {

    private final TicketService ticketService;

    public TicketValidationController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping(
            value = "/validate-qr",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<QrValidationResponse> validateQr(@PathVariable String eventCode,
                                                           @Valid @RequestBody QrValidationRequest request,
                                                           @RequestParam(name = "markUsed", defaultValue = "true") boolean markUsed) {
        TicketService.QrValidationResult result = ticketService.validateQrCodeForEntry(
                eventCode,
                request.qrCode(),
                markUsed
        );

        return ResponseEntity.ok(new QrValidationResponse(result.valid(), result.reason()));
    }

    public record QrValidationRequest(
            @NotBlank(message = "qrCode is required")
            String qrCode
    ) {}

    public record QrValidationResponse(boolean valid, String reason) {}
}
