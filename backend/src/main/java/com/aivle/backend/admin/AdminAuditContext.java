package com.aivle.backend.admin;

import jakarta.servlet.http.HttpServletRequest;

public record AdminAuditContext(String requestId, String ipAddress, String userAgent) {
    public static AdminAuditContext from(HttpServletRequest request) {
        return new AdminAuditContext(
            trim(request.getHeader("X-Request-Id"), 100),
            trim(request.getRemoteAddr(), 64),
            trim(request.getHeader("User-Agent"), 500)
        );
    }

    private static String trim(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
