package com.aivle.backend.admin;

import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.entity.UserStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReauthenticationService {
    private final PasswordEncoder passwords;
    private final AdminActionTokenRepository tokens;
    private final Clock clock;
    private final AdminAuditService audits;
    private final UserRepository users;

    @Transactional
    public IssuedToken issue(
        User actor,
        String password,
        AdminActionPurpose purpose,
        AdminAuditContext context
    ) {
        User currentActor = currentAdmin(actor);
        if (purpose == null || !passwords.matches(password, currentActor.getPasswordHash())) {
            BusinessException failure =
                new BusinessException(ErrorCode.ADMIN_REAUTHENTICATION_FAILED);
            recordFailure(actor, purpose, failure, context);
            throw failure;
        }

        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plusMinutes(5);
        tokens.save(AdminActionToken.issue(
            currentActor.getId(),
            purpose.name(),
            hash(raw),
            expiresAt,
            currentActor.getSecurityVersion(),
            now
        ));
        audits.recordSuccess(
            currentActor.getId(),
            AdminAuditAction.ADMIN_REAUTHENTICATION_SUCCEEDED,
            AdminAuditTargetType.ADMIN_AUTH,
            currentActor.getId(),
            currentActor.getUsername(),
            null,
            Map.of(),
            Map.of("purpose", purpose.name()),
            context,
            Map.of("purpose", purpose.name())
        );
        return new IssuedToken(raw, expiresAt);
    }

    @Transactional
    public void requireAndConsume(
        User actor,
        String raw,
        AdminActionPurpose purpose,
        AdminAuditContext context
    ) {
        try {
            User currentActor = currentAdmin(actor);
            if (raw == null || raw.isBlank()) {
                throw new BusinessException(ErrorCode.REAUTHENTICATION_REQUIRED);
            }
            AdminActionToken token = tokens.findByTokenHash(hash(raw))
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_REAUTHENTICATION_FAILED));
            LocalDateTime now = LocalDateTime.now(clock);
            if (token.getUsedAt() != null) {
                throw new BusinessException(ErrorCode.ADMIN_ACTION_TOKEN_ALREADY_USED);
            }
            if (purpose == null || !token.getPurpose().equals(purpose.name())) {
                throw new BusinessException(ErrorCode.ADMIN_REAUTHENTICATION_PURPOSE_MISMATCH);
            }
            if (!token.getExpiresAt().isAfter(now)) {
                throw new BusinessException(ErrorCode.ADMIN_REAUTHENTICATION_EXPIRED);
            }
            if (!token.getActorUserId().equals(currentActor.getId())
                || !token.getSecurityVersion().equals(currentActor.getSecurityVersion())) {
                throw new BusinessException(ErrorCode.ADMIN_REAUTHENTICATION_FAILED);
            }
            token.consume(now);
        } catch (BusinessException failure) {
            recordFailure(actor, purpose, failure, context);
            throw failure;
        }
    }

    private User currentAdmin(User actor) {
        if (actor == null) throw new BusinessException(ErrorCode.ADMIN_ACCESS_REQUIRED);
        return users.findByIdAndDeletedAtIsNull(actor.getId())
            .filter(candidate -> candidate.getRole() == UserRole.ADMIN)
            .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_ACCESS_REQUIRED));
    }

    private void recordFailure(
        User actor,
        AdminActionPurpose purpose,
        BusinessException failure,
        AdminAuditContext context
    ) {
        if (actor == null) return;
        audits.recordFailureSafely(
            actor.getId(),
            AdminAuditAction.ADMIN_REAUTHENTICATION_FAILED,
            AdminAuditTargetType.ADMIN_AUTH,
            actor.getId(),
            actor.getUsername(),
            null,
            failure.getErrorCode().name(),
            context,
            purpose == null ? Map.of() : Map.of("purpose", purpose.name())
        );
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash admin action token", exception);
        }
    }

    public record IssuedToken(String actionToken, LocalDateTime expiresAt) { }
}
