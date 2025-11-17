package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Entity @Getter @Setter
@Table(name="tier_consumption")
public class TierConsumption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false) @JoinColumn(name="event_id")
    private Event event;

    @ManyToOne(optional=false) @JoinColumn(name="ticket_id")
    private Ticket ticket;

    /**
     * The vendor this ticket is locked to (if oneVendorOnly is enforced).
     * Once set, ticket can ONLY order from this vendor.
     */
    @ManyToOne @JoinColumn(name="locked_vendor_id")
    private Vendor lockedVendor;

    /**
     * Total items consumed (legacy field for maxItemsPerVendor).
     */
    private int totalItemsConsumed = 0;

    /**
     * Tracks how many items consumed from each category.
     * Key = category name, Value = count
     */
    @ElementCollection
    @CollectionTable(name = "tier_consumption_categories", 
                     joinColumns = @JoinColumn(name = "tier_consumption_id"))
    @MapKeyColumn(name = "category")
    @Column(name = "items_consumed")
    private Map<String, Integer> categoryConsumption = new HashMap<>();

    /**
     * Tracks items consumed per vendor.
     * Key = vendor ID, Value = count
     */
    @ElementCollection
    @CollectionTable(name = "tier_consumption_vendors", 
                     joinColumns = @JoinColumn(name = "tier_consumption_id"))
    @MapKeyColumn(name = "vendor_id")
    @Column(name = "items_consumed")
    private Map<Long, Integer> vendorConsumption = new HashMap<>();

    public void incrementCategory(String category, int amount) {
        categoryConsumption.merge(category, amount, Integer::sum);
    }

    public void incrementVendor(Long vendorId, int amount) {
        vendorConsumption.merge(vendorId, amount, Integer::sum);
    }

    public int getCategoryConsumption(String category) {
        return categoryConsumption.getOrDefault(category, 0);
    }

    public int getVendorConsumption(Long vendorId) {
        return vendorConsumption.getOrDefault(vendorId, 0);
    }
}
