package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.Transient;

@Entity @Getter @Setter
@Table(name="events")
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private String name;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    // Feature flags
    private boolean enableGuestPickupConfirm = true;
    private boolean requireVendorPinForPickup = false;
    private boolean enableMultiVendorCart = true;
    private boolean blockAddWhenOpenOrder = true;
    private boolean regularOneItemPerVendor = true;

    // Non-persistent helper used for views: loaded on-demand by controllers/services
    @Transient
    private List<TierPolicy> tierPolicies;
}
