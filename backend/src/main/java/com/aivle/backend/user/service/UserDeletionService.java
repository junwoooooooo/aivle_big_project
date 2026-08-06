package com.aivle.backend.user.service;

import com.aivle.backend.admin.AdminActionPurpose;
import com.aivle.backend.admin.AdminAuditAction;
import com.aivle.backend.admin.AdminAuditContext;
import com.aivle.backend.admin.AdminAuditService;
import com.aivle.backend.admin.AdminAuditTargetType;
import com.aivle.backend.admin.AdminReauthenticationService;
import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.auth.RefreshTokenRepository;
import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.entity.UserStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDeletionService {
    private static final String CONFIRMATION = "회원탈퇴";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final ServicePolicyService servicePolicy;
    private final AdminReauthenticationService reauthentication;
    private final AdminAuditService audits;
    private final Clock jobClock;

    @Transactional
    public void deleteSelf(
        Long userId,
        String password,
        String confirmation,
        String reason,
        AdminAuditContext context
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User target = lockedUser(userId);
        if (target.isDeleted()) throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_DELETED);
        if (target.getRole() != UserRole.USER) {
            throw new BusinessException(ErrorCode.ADMIN_SELF_DELETE_NOT_ALLOWED);
        }
        if (!CONFIRMATION.equals(confirmation)) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETION_CONFIRMATION_INVALID);
        }
        if (!passwords.matches(password, target.getPasswordHash())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETION_PASSWORD_INVALID);
        }
        delete(
            target,
            normalizedReason(reason, "본인 회원 탈퇴"),
            AdminAuditAction.USER_SELF_DELETED,
            target.getId(),
            context
        );
    }

    @Transactional
    public void deleteByAdmin(
        User actor,
        Long userId,
        String reason,
        String actionToken,
        AdminAuditContext context
    ) {
        if (actionToken == null || actionToken.isBlank()) {
            throw new BusinessException(ErrorCode.USER_DELETE_REAUTHENTICATION_REQUIRED);
        }
        reauthentication.requireAndConsume(
            actor, actionToken, AdminActionPurpose.USER_DELETE, context
        );
        User target = lockedUser(userId);
        if (target.isDeleted()) throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_DELETED);
        if (actor.getId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.ADMIN_SELF_DELETE_NOT_ALLOWED);
        }
        if (target.getRole() == UserRole.ADMIN
            && target.getStatus() == UserStatus.ACTIVE
            && users.findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE).size() <= 1) {
            throw new BusinessException(ErrorCode.LAST_ACTIVE_ADMIN_DELETE_NOT_ALLOWED);
        }
        delete(
            target,
            normalizedReason(reason, "관리자 사용자 삭제"),
            AdminAuditAction.USER_DELETED_BY_ADMIN,
            actor.getId(),
            context
        );
    }

    private void delete(
        User target,
        String reason,
        AdminAuditAction action,
        Long actorUserId,
        AdminAuditContext context
    ) {
        UserStatus before = target.getStatus();
        LocalDateTime now = LocalDateTime.now(jobClock);
        target.anonymizeAndDelete(anonymousUsername(target.getId()), reason, now);
        refreshTokens.findAllByUserIdAndDeletedAtIsNull(target.getId())
            .forEach(token -> token.revoke(now));
        audits.recordSuccess(
            actorUserId,
            action,
            AdminAuditTargetType.USER,
            target.getId(),
            "탈퇴한 사용자",
            reason,
            Map.of("accountState", before.name()),
            Map.of("accountState", "DELETED"),
            context,
            Map.of()
        );
    }

    private User lockedUser(Long userId) {
        return users.findByIdForDeletionUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private String anonymousUsername(Long userId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "deleted-" + Long.toUnsignedString(userId, 36) + "-" + suffix;
    }

    private String normalizedReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) return fallback;
        return reason.trim();
    }
}
