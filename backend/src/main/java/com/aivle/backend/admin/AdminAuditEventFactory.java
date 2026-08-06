package com.aivle.backend.admin;

import com.aivle.backend.audit.AuditEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.aivle.backend.user.repository.UserRepository;

@Component
@RequiredArgsConstructor
class AdminAuditEventFactory {
    private static final Set<String> FORBIDDEN_KEY_PARTS = Set.of(
        "password", "accesstoken", "refreshtoken", "actiontoken",
        "authorization", "cookie", "storagekey", "documentcontent"
    );

    private final ObjectMapper objectMapper;
    private final Clock jobClock;
    private final UserRepository users;

    AuditEvent create(AdminAuditRecord record) {
        validate(record.before());
        validate(record.after());
        validate(record.metadata());
        AdminAuditContext context = record.context() == null
            ? new AdminAuditContext(null, null, null)
            : record.context();
        return AuditEvent.adminRecord(
            record.actorUserId(),
            users.findById(record.actorUserId()).map(user -> user.getRole().name()).orElse(null),
            record.action().name(),
            record.targetType().name(),
            record.targetId(),
            trim(record.targetLabel(), 255),
            record.result(),
            trim(record.reason(), 500),
            json(record.before()),
            json(record.after()),
            trim(record.errorCode(), 100),
            context.requestId(),
            context.ipAddress(),
            context.userAgent(),
            json(record.metadata()),
            LocalDateTime.now(jobClock)
        );
    }

    private void validate(Map<String, ?> values) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            validateKey(entry.getKey());
            if (entry.getValue() instanceof Map<?, ?> nested) {
                for (Object key : nested.keySet()) if (key != null) validateKey(key.toString());
            }
        }
    }

    private void validateKey(String key) {
        String normalized = key.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
        if (FORBIDDEN_KEY_PARTS.stream().anyMatch(normalized::contains)) {
            throw new IllegalArgumentException("sensitive audit field is not allowed");
        }
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("admin audit serialization failed", exception);
        }
    }

    private String trim(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
