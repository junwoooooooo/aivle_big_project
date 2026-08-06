package com.aivle.backend.admin;

import com.aivle.backend.auth.RefreshTokenRepository;
import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.entity.UserStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final UserRepository users;
    private final ProjectRepository projects;
    private final RefreshTokenRepository refreshTokens;
    private final AdminAuditService audits;
    private final AdminReauthenticationService reauthentication;
    private final Clock jobClock;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(String keyword, UserRole role, UserStatus status, Pageable pageable) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long activeAdminCount = users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE);
        return users.searchAdminUsers(normalized, role, status, pageable)
            .map(user -> response(user, activeAdminCount));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse detail(Long userId) {
        return response(find(userId), users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE));
    }

    @Transactional
    public AdminUserResponse changeStatus(User actor, Long userId, UserStatus status, String reason,
                                          String actionToken, AdminAuditContext context) {
        if (status == UserStatus.DISABLED) {
            reauthentication.requireAndConsume(
                actor, actionToken, AdminActionPurpose.USER_DISABLE, context
            );
        }
        User target = find(userId);
        if (actor.getId().equals(target.getId()) && status != UserStatus.ACTIVE) throw new BusinessException(ErrorCode.SELF_ADMIN_ACCOUNT_CHANGE_NOT_ALLOWED);
        if (target.getStatus() == status) {
            if (status == UserStatus.LOCKED) throw new BusinessException(ErrorCode.USER_ALREADY_LOCKED);
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (target.getRole() == UserRole.ADMIN && target.getStatus() == UserStatus.ACTIVE
            && status != UserStatus.ACTIVE && activeAdminsForUpdate().size() <= 1) {
            throw new BusinessException(ErrorCode.LAST_ACTIVE_ADMIN_REQUIRED);
        }
        UserStatus before = target.getStatus();
        target.updateStatus(status, reason.trim(), LocalDateTime.now(jobClock));
        target.advanceSecurityVersion();
        revoke(target.getId());
        audits.recordSuccess(
            actor.getId(), AdminAuditAction.USER_STATUS_CHANGED, AdminAuditTargetType.USER,
            target.getId(), target.getUsername(), reason.trim(),
            Map.of("status", before.name()), Map.of("status", status.name()),
            context, Map.of()
        );
        return response(target);
    }

    @Transactional
    public AdminUserResponse changeRole(User actor, Long userId, UserRole role, String reason,
                                        String actionToken, AdminAuditContext context) {
        reauthentication.requireAndConsume(
            actor, actionToken, AdminActionPurpose.USER_ROLE_CHANGE, context
        );
        User target = find(userId);
        if (actor.getId().equals(target.getId()) && target.getRole() == UserRole.ADMIN && role != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.SELF_ADMIN_ROLE_CHANGE_NOT_ALLOWED);
        }
        if (target.getRole() == role) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (target.getRole() == UserRole.ADMIN && target.getStatus() == UserStatus.ACTIVE && role != UserRole.ADMIN && activeAdminsForUpdate().size() <= 1) {
            throw new BusinessException(ErrorCode.LAST_ACTIVE_ADMIN_REQUIRED);
        }
        UserRole before = target.getRole();
        target.updateRole(role, actor.getId(), LocalDateTime.now(jobClock));
        target.advanceSecurityVersion();
        revoke(target.getId());
        audits.recordSuccess(
            actor.getId(), AdminAuditAction.USER_ROLE_CHANGED, AdminAuditTargetType.USER,
            target.getId(), target.getUsername(), reason.trim(),
            Map.of("role", before.name()), Map.of("role", role.name()),
            context, Map.of()
        );
        return response(target);
    }

    @Transactional
    public void revokeSessions(User actor, Long userId, String reason, AdminAuditContext context) {
        if (actor.getId().equals(userId)) throw new BusinessException(ErrorCode.SELF_SESSION_REVOKE_NOT_ALLOWED);
        User target = find(userId);
        target.advanceSecurityVersion();
        revoke(userId);
        audits.recordSuccess(
            actor.getId(), AdminAuditAction.USER_SESSION_REVOKED, AdminAuditTargetType.USER,
            userId, target.getUsername(), reason.trim(),
            Map.of(), Map.of("sessions", "REVOKED"), context, Map.of()
        );
    }

    private void revoke(Long userId) {
        LocalDateTime now = LocalDateTime.now(jobClock);
        refreshTokens.findAllByUserIdAndDeletedAtIsNull(userId).forEach(token -> token.revoke(now));
    }
    private java.util.List<User> activeAdminsForUpdate() { return users.findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE); }
    private User find(Long userId) { return users.findByIdAndDeletedAtIsNull(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); }
    private AdminUserResponse response(User user) {
        return response(user, users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE));
    }
    private AdminUserResponse response(User user, long activeAdminCount) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getName(), user.getRole().name(),
            user.getStatus().name(), projects.countByOwnerIdAndDeletedAtIsNull(user.getId()), user.getLastLoginAt(), user.getCreatedAt(),
            user.getOrganizationName(), user.getDepartmentName(), user.getJobTitle(), user.getSecurityVersion(),
            user.getLockedAt(), user.getLockedReason(), user.getDisabledAt(), user.getDisabledReason(),
            user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE && activeAdminCount <= 1);
    }

    public record AdminUserResponse(Long id, String username, String email, String displayName, String role, String accountStatus,
                                    long projectCount, LocalDateTime lastLoginAt, LocalDateTime createdAt,
                                    String organizationName, String departmentName, String jobTitle, Long securityVersion,
                                    LocalDateTime lockedAt, String lockedReason, LocalDateTime disabledAt,
                                    String disabledReason, boolean lastActiveAdmin) { }
}
