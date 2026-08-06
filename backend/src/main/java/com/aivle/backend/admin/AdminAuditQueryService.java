package com.aivle.backend.admin;

import com.aivle.backend.audit.AuditEvent;
import com.aivle.backend.audit.AuditEventRepository;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AdminAuditQueryService {
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };

    private final AuditEventRepository events;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<AuditListItem> list(AuditQuery filter, Pageable pageable) {
        return events.findAll(specification(filter), pageable).map(this::listItem);
    }

    @Transactional(readOnly = true)
    public AuditDetail detail(Long auditId) {
        AuditEvent event = events.findWithActorById(auditId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUDIT_EVENT_NOT_FOUND));
        ParsedMetadata parsed = metadata(event.getMetadataJson());
        TargetSummary target = target(event, true);
        return new AuditDetail(
            event.getId(), event.getOccurredAt(), actor(event), event.getEventType(), target,
            result(event), reason(event, parsed.values()), object(event.getBeforeJson(), legacyChange(event, parsed.values(), "before")),
            object(event.getAfterJson(), legacyChange(event, parsed.values(), "after")),
            event.getErrorCode(), event.getRequestId(), event.getIpAddress(), event.getUserAgent(),
            remainingMetadata(parsed.values()), parsed.parseError()
        );
    }

    private Specification<AuditEvent> specification(AuditQuery filter) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            var actor = root.join("actor", JoinType.LEFT);
            if (hasText(filter.actor())) {
                String pattern = contains(filter.actor());
                var actorPredicates = new ArrayList<Predicate>();
                actorPredicates.add(builder.like(builder.lower(actor.get("username")), pattern));
                actorPredicates.add(builder.like(builder.lower(actor.get("name")), pattern));
                parseLong(filter.actor()).ifPresent(id -> actorPredicates.add(builder.equal(root.get("actorUserId"), id)));
                predicates.add(builder.or(actorPredicates.toArray(Predicate[]::new)));
            }
            if (filter.action() != null) predicates.add(builder.equal(root.get("eventType"), filter.action().name()));
            if (filter.result() != null) predicates.add(builder.equal(root.get("result"), filter.result().name()));
            if (filter.targetType() != null) predicates.add(builder.equal(root.get("aggregateType"), filter.targetType().name()));
            if (hasText(filter.requestId())) {
                predicates.add(builder.equal(builder.lower(root.get("requestId")), filter.requestId().trim().toLowerCase(Locale.ROOT)));
            }
            if (filter.occurredFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), filter.occurredFrom().atStartOfDay()));
            }
            if (filter.occurredTo() != null) {
                predicates.add(builder.lessThan(root.get("occurredAt"), filter.occurredTo().plusDays(1).atStartOfDay()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AuditListItem listItem(AuditEvent event) {
        ParsedMetadata parsed = metadata(event.getMetadataJson());
        return new AuditListItem(
            event.getId(), event.getOccurredAt(), actor(event), event.getEventType(),
            target(event, false), result(event), reason(event, parsed.values()), event.getRequestId()
        );
    }

    private ActorSummary actor(AuditEvent event) {
        if (event.getActor() == null) return new ActorSummary(event.getActorUserId(), null, null, null);
        return new ActorSummary(
            event.getActor().getId(), event.getActor().getUsername(),
            event.getActor().getName(), hasText(event.getActorRole()) ? event.getActorRole() : event.getActor().getRole().name()
        );
    }

    private TargetSummary target(AuditEvent event, boolean checkExists) {
        String type = hasText(event.getAggregateType()) ? event.getAggregateType() : AdminAuditTargetType.OTHER.name();
        String id = event.getAggregateId() == null ? null : event.getAggregateId().toString();
        boolean exists = !checkExists || targetExists(type, event.getAggregateId());
        return new TargetSummary(type, id, event.getTargetLabel(), exists);
    }

    private boolean targetExists(String type, Long id) {
        if (id == null) return false;
        if (AdminAuditTargetType.USER.name().equals(type)) return users.findByIdAndDeletedAtIsNull(id).isPresent();
        if (AdminAuditTargetType.PROJECT.name().equals(type)) return projects.findByIdAndDeletedAtIsNull(id).isPresent();
        return false;
    }

    private String result(AuditEvent event) {
        return hasText(event.getResult()) ? event.getResult() : AdminAuditResult.SUCCESS.name();
    }

    private String reason(AuditEvent event, Map<String, Object> metadata) {
        if (hasText(event.getReason())) return event.getReason();
        Object legacy = metadata.get("reason");
        return legacy == null ? null : legacy.toString();
    }

    private Map<String, Object> legacyChange(AuditEvent event, Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) return Map.of();
        String field = event.getEventType().contains("ROLE") ? "role"
            : event.getEventType().contains("STATUS") ? "status"
            : event.getEventType().contains("SETTING") ? "value"
            : "value";
        return Map.of(field, value);
    }

    private Map<String, Object> object(String json, Map<String, Object> fallback) {
        if (!hasText(json)) return fallback;
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private ParsedMetadata metadata(String json) {
        if (!hasText(json)) return new ParsedMetadata(Map.of(), false);
        try {
            return new ParsedMetadata(objectMapper.readValue(json, OBJECT_MAP), false);
        } catch (RuntimeException exception) {
            return new ParsedMetadata(Map.of(), true);
        }
    }

    private Map<String, Object> remainingMetadata(Map<String, Object> values) {
        Map<String, Object> remaining = new LinkedHashMap<>(values);
        remaining.keySet().removeAll(java.util.Set.of("before", "after", "reason"));
        return Map.copyOf(remaining);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String contains(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static java.util.Optional<Long> parseLong(String value) {
        try {
            return java.util.Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }

    public record AuditQuery(
        String actor,
        AdminAuditAction action,
        AdminAuditResult result,
        AdminAuditTargetType targetType,
        String requestId,
        LocalDate occurredFrom,
        LocalDate occurredTo
    ) { }

    public record ActorSummary(Long id, String username, String displayName, String role) { }
    public record TargetSummary(String type, String id, String label, boolean exists) { }

    public record AuditListItem(
        Long id,
        LocalDateTime occurredAt,
        ActorSummary actor,
        String action,
        TargetSummary target,
        String result,
        String reason,
        String requestId
    ) { }

    public record AuditDetail(
        Long id,
        LocalDateTime occurredAt,
        ActorSummary actor,
        String action,
        TargetSummary target,
        String result,
        String reason,
        Map<String, Object> before,
        Map<String, Object> after,
        String errorCode,
        String requestId,
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata,
        boolean metadataParseError
    ) { }

    private record ParsedMetadata(Map<String, Object> values, boolean parseError) { }
}
