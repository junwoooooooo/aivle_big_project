package com.aivle.backend.pipeline.idea.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class IdeaBriefIdempotencyPolicy {
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    public String require(String rawKey) {
        String normalized = rawKey == null ? null : rawKey.trim();
        if (normalized == null || !ALLOWED.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return normalized;
    }
}
