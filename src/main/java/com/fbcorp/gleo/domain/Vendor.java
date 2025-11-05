package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fbcorp.gleo.model.VendorStatus;

@Entity
@Getter
@Setter
@Table(name = "vendors")
public class Vendor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Event event;

    @Column(nullable = false)
    private String name;

    private boolean active = true;

    private String imagePath;

    // optional: PIN hash (demo simplifies to plain)
    private String pinPlain;
    
    @Enumerated(EnumType.STRING)
    private VendorStatus status = VendorStatus.AVAILABLE;
}