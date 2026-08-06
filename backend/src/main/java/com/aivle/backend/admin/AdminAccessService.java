package com.aivle.backend.admin;

import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccessService {
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public User requireAdmin() {
        User user = users.findByIdAndDeletedAtIsNull(currentUserProvider.currentUserId())
            .filter(candidate -> candidate.getRole() == UserRole.ADMIN && candidate.canLogin())
            .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_ACCESS_REQUIRED));
        return user;
    }
}
