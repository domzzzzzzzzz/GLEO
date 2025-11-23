package com.fbcorp.gleo.domain;

public enum TierCode {
    VIP, REG;

    /**
     * Case-insensitive lookup that returns {@code null} when the code is unknown.
     */
    public static TierCode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (TierCode value : values()) {
            if (value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }
}
