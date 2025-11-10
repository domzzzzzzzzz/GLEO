package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity @Getter @Setter
@Table(name="menu_items")
public class MenuItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Vendor vendor;

    @Column(nullable=false)
    private String name;

    private BigDecimal price = BigDecimal.ZERO;
    private boolean available = true;
    private Integer maxPerOrder; // nullable

    // Path or URL to the menu item image (optional)
    private String imagePath;
}
