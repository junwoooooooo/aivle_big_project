package com.aivle.backend.common.security;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !dev-header-auth")
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {
    @Override
    public Long currentUserId() {
        Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
    }
}
