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
    
    // Optional free-text category for grouping (e.g. Burgers, Drinks, Fries)
    private String category;
    
    // Numeric order for the category. Lower numbers appear first.
    @Column(name = "category_order")
    private Integer categoryOrder = 0;

    // Order of the item within its category.
    @Column(name = "item_order")
    private Integer itemOrder = 0;

    // Inventory tracking (null means unlimited)
    @Column(name = "stock_level")
    private Integer stockLevel;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;
}
