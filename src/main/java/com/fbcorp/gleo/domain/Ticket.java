package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(name="tickets")
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Event event;

    @Column(unique = true, nullable = false)
    private String qrCode;

    @Enumerated(EnumType.STRING)
    private TierCode tierCode;

    private String holderName;
    private String holderPhone;
    private String serial;

    private String boundDeviceHash; // nullable; first device bind
    private boolean active = true;

    @Column(name = "checked_in", nullable = false)
    private boolean checkedIn = false;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    public boolean isRegular(){
        return tierCode == TierCode.REG;
    }
    public boolean isVip(){
        return tierCode == TierCode.VIP;
    }
}
