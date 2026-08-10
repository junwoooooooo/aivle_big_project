package com.aivle.backend.pipeline.integration.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalModuleAuthenticator {
    private final byte[] expected;
    public InternalModuleAuthenticator(@Value("${app.market-integration.internal-api-key:}") String key) {
        this.expected = key.strip().getBytes(StandardCharsets.UTF_8);
    }
    public void require(String supplied) {
        byte[] candidate = supplied == null ? new byte[0] : supplied.strip().getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0 || !MessageDigest.isEqual(expected, candidate))
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "유효한 내부 Module 인증이 필요합니다.");
    }
}
