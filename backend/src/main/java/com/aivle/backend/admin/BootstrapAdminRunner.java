package com.aivle.backend.admin;

import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import com.aivle.backend.auth.AuthService;
import com.aivle.backend.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminRunner implements ApplicationRunner {
    private final BootstrapAdminProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DomainAuditService audits;
    private final Clock jobClock;
    private final AuthService authService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) return;
        if (blank(properties.username()) || blank(properties.email()) || blank(properties.password())) {
            throw new IllegalStateException("Bootstrap admin is enabled but required environment variables are missing.");
        }
        String username = properties.username().trim().toLowerCase(Locale.ROOT);
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        authService.validateBootstrapCredentials(username, properties.password(), "Bootstrap Admin");
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalStateException("Bootstrap admin email is invalid.");
        var usernameMatch = users.findByUsername(username);
        var emailMatch = users.findByEmailIgnoreCase(email);
        if (usernameMatch.isPresent() || emailMatch.isPresent()) {
            if (usernameMatch.isPresent() && emailMatch.isPresent() && usernameMatch.get().getId().equals(emailMatch.get().getId())
                && usernameMatch.get().getRole() == UserRole.ADMIN) {
                log.info("Bootstrap admin creation skipped because the configured ADMIN already exists.");
                return;
            }
            throw new IllegalStateException("Bootstrap admin username or email belongs to a different account.");
        }
        User user = User.register(username, email, passwordEncoder.encode(properties.password()), "Bootstrap Admin", null, null, null);
        user.updateRole(UserRole.ADMIN, null, LocalDateTime.now(jobClock));
        users.save(user);
        audits.record(user.getId(), null, AuditEventType.BOOTSTRAP_ADMIN_CREATED, "USER", user.getId(), null, Map.of("status", "ACTIVE"));
        log.info("Bootstrap admin created for username {}.", username);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
