package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    public boolean isRegular(){
        return tierCode == TierCode.REG;
    }
    public boolean isVip(){
        return tierCode == TierCode.VIP;
    }
}
