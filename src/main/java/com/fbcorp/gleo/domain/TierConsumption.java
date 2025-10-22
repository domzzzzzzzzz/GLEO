package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter
@Table(name="tier_consumption", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "ticket_id", "vendor_id"}))
public class TierConsumption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false) @JoinColumn(name="event_id")
    private Event event;

    @ManyToOne(optional=false) @JoinColumn(name="ticket_id")
    private Ticket ticket;

    @ManyToOne(optional=false) @JoinColumn(name="vendor_id")
    private Vendor vendor;

    private int totalItemsConsumed = 0;
}
