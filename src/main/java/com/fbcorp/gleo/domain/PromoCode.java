package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter
@Setter
@Table(name = "promo_codes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id", "code"})
})
public class PromoCode {

    public enum DiscountType {
        PERCENTAGE,
        AMOUNT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Event event;

    @Column(nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DiscountType discountType = DiscountType.AMOUNT;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @PrePersist
    @PreUpdate
    public void normalize() {
        if (code != null) {
            code = code.trim().toUpperCase();
        }
        if (discountValue != null) {
            discountValue = discountValue.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0 || discountValue == null) {
            return BigDecimal.ZERO;
        }

        if (discountType == DiscountType.PERCENTAGE) {
            BigDecimal percent = discountValue.compareTo(BigDecimal.ZERO) < 0
                    ? BigDecimal.ZERO
                    : discountValue.min(BigDecimal.valueOf(100));
            return subtotal.multiply(percent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        BigDecimal amount = discountValue;
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            amount = BigDecimal.ZERO;
        }
        return amount;
    }
}
