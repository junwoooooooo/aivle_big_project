package com.aivle.backend.admin;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.persona.catalog.BaselinePersonaCatalog;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import com.aivle.backend.persona.catalog.entity.ClusterPersonaPolicy;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import com.aivle.backend.persona.catalog.repository.ClusterPersonaPolicyRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AdminClusterPersonaService {
    public static final int MAX_VISIBLE = 6;

    private final BaselinePersonaRepository personas;
    private final ClusterPersonaPolicyRepository policies;
    private final UserRepository users;
    private final ServicePolicyService servicePolicy;
    private final AdminAuditService audits;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PersonaPolicyResponse> list() {
        Map<Long, ClusterPersonaPolicy> byPersonaId = policies.findAllByOrderByDisplayOrderAsc()
            .stream()
            .collect(Collectors.toMap(value -> value.getPersona().getId(), Function.identity()));
        Map<Long, User> updaters = updaterMap(byPersonaId.values().stream()
            .map(ClusterPersonaPolicy::getUpdatedByUserId)
            .filter(value -> value != null)
            .distinct()
            .toList());
        return personas.findByCatalogVersionAndDeletedAtIsNullOrderByDisplayOrder(
                BaselinePersonaCatalog.VERSION
            ).stream()
            .map(persona -> response(persona, byPersonaId.get(persona.getId()), updaters))
            .sorted((left, right) -> {
                if (left.enabled() != right.enabled()) return left.enabled() ? -1 : 1;
                return Integer.compare(left.displayOrder(), right.displayOrder());
            })
            .toList();
    }

    @Transactional
    public PersonaPolicyResponse changeVisibility(
        User actor,
        Long personaId,
        boolean enabled,
        String reason,
        AdminAuditContext context
    ) {
        BaselinePersona persona = lockedCatalog().stream()
            .filter(value -> value.getId().equals(personaId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.CLUSTER_PERSONA_NOT_FOUND));
        ClusterPersonaPolicy policy = policies.findByPersonaId(personaId).orElse(null);
        boolean before = policy != null && policy.isEnabled();
        if (before == enabled) throw new BusinessException(ErrorCode.SERVICE_SETTING_ALREADY_APPLIED);

        long enabledCount = policies.countByEnabledTrue();
        if (enabled && enabledCount >= MAX_VISIBLE) {
            throw new BusinessException(ErrorCode.CLUSTER_PERSONA_LIMIT_EXCEEDED);
        }
        if (!enabled && servicePolicy.isClusterPersonaEnabled() && enabledCount <= 1) {
            throw new BusinessException(ErrorCode.CLUSTER_PERSONA_SELECTION_REQUIRED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int beforeOrder = policy == null ? persona.getDisplayOrder() : policy.getDisplayOrder();
        if (policy == null) {
            int nextOrder = enabled
                ? policies.findByEnabledTrueOrderByDisplayOrderAsc().stream()
                    .mapToInt(ClusterPersonaPolicy::getDisplayOrder)
                    .max()
                    .orElse(0) + 1
                : persona.getDisplayOrder();
            policy = ClusterPersonaPolicy.create(
                persona, enabled, nextOrder, actor.getId(), now
            );
        } else {
            policy.updateVisibility(enabled, actor.getId(), now);
        }
        policies.save(policy);
        audits.recordSuccess(
            actor.getId(),
            AdminAuditAction.CLUSTER_PERSONA_VISIBILITY_CHANGED,
            AdminAuditTargetType.PERSONA,
            persona.getId(),
            persona.getDisplayName(),
            reason.trim(),
            Map.of("enabled", before, "displayOrder", beforeOrder),
            Map.of("enabled", enabled, "displayOrder", policy.getDisplayOrder()),
            context,
            Map.of("personaId", persona.getId())
        );
        return response(persona, policy, Map.of(actor.getId(), actor));
    }

    @Transactional
    public List<PersonaPolicyResponse> reorder(
        User actor,
        List<Long> personaIds,
        String reason,
        AdminAuditContext context
    ) {
        lockedCatalog();
        List<ClusterPersonaPolicy> enabled = policies.findByEnabledTrueOrderByDisplayOrderAsc();
        LinkedHashSet<Long> requested = new LinkedHashSet<>(personaIds);
        if (personaIds.isEmpty()
            || personaIds.size() > MAX_VISIBLE
            || requested.size() != personaIds.size()
            || !requested.equals(enabled.stream()
                .map(value -> value.getPersona().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new)))) {
            throw new BusinessException(ErrorCode.SERVICE_SETTING_INVALID);
        }
        Map<Long, Integer> before = enabled.stream().collect(Collectors.toMap(
            value -> value.getPersona().getId(),
            ClusterPersonaPolicy::getDisplayOrder
        ));
        LocalDateTime now = LocalDateTime.now(clock);
        for (int index = 0; index < personaIds.size(); index++) {
            ClusterPersonaPolicy policy = policies.findByPersonaId(personaIds.get(index))
                .orElseThrow(() -> new BusinessException(ErrorCode.CLUSTER_PERSONA_NOT_ALLOWED));
            policy.reorder(index + 1, actor.getId(), now);
        }
        audits.recordSuccess(
            actor.getId(),
            AdminAuditAction.CLUSTER_PERSONA_ORDER_CHANGED,
            AdminAuditTargetType.PERSONA,
            null,
            "군집 페르소나 표시 순서",
            reason.trim(),
            Map.of("order", before),
            Map.of("order", personaIds),
            context,
            Map.of()
        );
        return list();
    }

    private List<BaselinePersona> lockedCatalog() {
        return personas.lockActiveCatalog(BaselinePersonaCatalog.VERSION);
    }

    private PersonaPolicyResponse response(
        BaselinePersona persona,
        ClusterPersonaPolicy policy,
        Map<Long, User> updaters
    ) {
        UpdatedByResponse updater = null;
        if (policy != null && policy.getUpdatedByUserId() != null) {
            User user = updaters.get(policy.getUpdatedByUserId());
            updater = user == null
                ? new UpdatedByResponse(policy.getUpdatedByUserId(), null, null)
                : new UpdatedByResponse(user.getId(), user.getUsername(), user.getName());
        }
        return new PersonaPolicyResponse(
            persona.getId(),
            persona.getDisplayName(),
            persona.getDescription(),
            keywords(persona.getKeyTraitsJson()),
            policy != null && policy.isEnabled(),
            policy == null ? persona.getDisplayOrder() : policy.getDisplayOrder(),
            updater,
            policy == null ? null : policy.getUpdatedAt()
        );
    }

    private Map<Long, User> updaterMap(List<Long> ids) {
        return users.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private List<String> keywords(String json) {
        try {
            return Arrays.stream(objectMapper.readValue(json, String[].class))
                .filter(value -> value != null && !value.isBlank())
                .limit(3)
                .toList();
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    public record UpdatedByResponse(Long id, String username, String displayName) { }

    public record PersonaPolicyResponse(
        Long id,
        String name,
        String summary,
        List<String> keywords,
        boolean enabled,
        int displayOrder,
        UpdatedByResponse updatedBy,
        LocalDateTime updatedAt
    ) { }
}
