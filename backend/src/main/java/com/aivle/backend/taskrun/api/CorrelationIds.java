package com.aivle.backend.taskrun.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

final class CorrelationIds {
    private static final String ATTRIBUTE = CorrelationIds.class.getName();
    private CorrelationIds() {}
    static String resolve(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE); if (existing instanceof String value) return value;
        String supplied = request.getHeader("X-Correlation-Id");
        String value = supplied != null && supplied.matches("[A-Za-z0-9._-]{1,128}") ? supplied : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, value); return value;
    }
}
