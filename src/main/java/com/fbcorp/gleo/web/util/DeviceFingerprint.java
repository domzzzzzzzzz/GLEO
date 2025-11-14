package com.fbcorp.gleo.web.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;

public final class DeviceFingerprint {
    private DeviceFingerprint() {}

    public static String from(HttpServletRequest request) {
        if (request == null) return "";
        String ua = request.getHeader("User-Agent");
        String ip = request.getRemoteAddr();
        return Integer.toHexString(Objects.hash(ua, ip));
    }
}
