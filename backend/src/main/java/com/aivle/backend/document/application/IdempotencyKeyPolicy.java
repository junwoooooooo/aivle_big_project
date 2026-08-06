package com.aivle.backend.document.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class IdempotencyKeyPolicy {
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    public String normalize(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String normalized = rawKey.trim();
        if (!ALLOWED.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return normalized;
    }
}
