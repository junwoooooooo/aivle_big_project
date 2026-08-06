package com.aivle.backend.common.security;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile({"test", "dev-header-auth"})
@RequiredArgsConstructor
public class RequestHeaderCurrentUserProvider implements CurrentUserProvider {
    private final HttpServletRequest request;

    @Override
    public Long currentUserId() {
        String value = request.getHeader("X-User-Id");
        if (value == null || value.isBlank()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
            }
            value = authentication.getName();
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "X-User-Id 헤더가 올바르지 않습니다.");
        }
    }
}
