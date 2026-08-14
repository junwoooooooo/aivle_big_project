package com.aivle.backend.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestIds {
    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = "requestId";
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private RequestIds() {
    }

    public static String resolve(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof String value && ALLOWED.matcher(value).matches()) {
            return value;
        }
        String supplied = request.getHeader(HEADER);
        String resolved = supplied != null && ALLOWED.matcher(supplied).matches()
            ? supplied
            : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, resolved);
        return resolved;
    }
}
