package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Entity
@Getter
@Setter
@Table(name = "tier_policies",
       uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "tier_code"}))
public class TierPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier_code", nullable = false)
    private TierCode tierCode;

    @Column(nullable = false)
    private boolean unlimited = true;

    /**
     * Maximum items that can be consumed per vendor for this tier.
     * Null represents "no explicit limit"; this field is only meaningful when {@link #unlimited} is false.
     */
    private Integer maxItemsPerVendor;

    /**
     * If true, this tier can only order from ONE vendor for the entire event.
     * Once they order from a vendor, they cannot order from any other vendor.
     */
    @Column(name = "one_vendor_only")
    private boolean oneVendorOnly = false;

    /**
     * If true, this tier can place ONLY ONE order total. After the first order
     * is placed, all vendors should be locked for this ticket.
     */
    @Column(name = "single_order_only")
    private boolean singleOrderOnly = false;

    /**
     * If true, once the guest places an order with a vendor, that vendor is
     * locked for the rest of the event (but other vendors remain available).
     */
    @Column(name = "lock_vendor_after_order")
    private boolean lockVendorAfterOrder = false;

    /**
     * If true, this tier can only order ONE item per category.
     * For example: one item from "Burgers", one from "Drinks", etc.
     */
    @Column(name = "one_item_per_category")
    private boolean oneItemPerCategory = false;

    /**
     * Per-category item limits. Key = category name, Value = max items allowed.
     * Example: {"Burgers": 1, "Drinks": 1, "Sides": 1}
     * This allows fine-grained control over how many items from each category.
     */
    @ElementCollection
    @CollectionTable(name = "tier_category_limits", 
                     joinColumns = @JoinColumn(name = "tier_policy_id"))
    @MapKeyColumn(name = "category")
    @Column(name = "max_items")
    private Map<String, Integer> categoryLimits = new HashMap<>();

    public boolean hasLimit() {
        return !unlimited && maxItemsPerVendor != null;
    }

    public boolean hasVendorRestriction() {
        return oneVendorOnly;
    }

    public boolean hasCategoryRestriction() {
        return oneItemPerCategory || !categoryLimits.isEmpty();
    }

    public Integer getCategoryLimit(String category) {
        if (oneItemPerCategory) return 1;
        if (category == null) return null;
        String trimmed = category.trim();
        Integer limit = categoryLimits.get(trimmed);
        if (limit == null && !trimmed.equals(category)) {
            limit = categoryLimits.get(category);
        }
        return limit;
    }
}
