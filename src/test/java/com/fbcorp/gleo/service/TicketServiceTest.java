package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Ticket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TicketServiceTest {

    @Autowired
    private TicketService ticketService;

    @Test
    void resolvesWalkInTicketWhenQrMissing() {
        Ticket first = ticketService.resolveTicket("G2025", null, "device123");
        assertThat(first).isNotNull();
        assertThat(first.getQrCode()).startsWith("WALKIN-G2025-");

        Ticket second = ticketService.resolveTicket("G2025", "", "device123");
        assertThat(second.getId()).isEqualTo(first.getId());
    }
}
