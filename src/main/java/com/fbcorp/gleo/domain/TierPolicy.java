package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    public boolean hasLimit() {
        return !unlimited && maxItemsPerVendor != null;
    }
}
