package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "tiers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "code"}))
public class Tier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    /**
     * Short, machine-readable code (e.g., VIP, REG, STAFF). Uppercase, unique per event.
     */
    @Column(nullable = false, length = 32)
    private String code;

    /**
     * Friendly display name (e.g., "VIP", "Regular", "Staff").
     */
    @Column(nullable = false, length = 128)
    private String name;

    /**
     * Sort order for displaying tiers in admin UIs.
     */
    @Column(name = "display_order")
    private Integer displayOrder = 0;
}
