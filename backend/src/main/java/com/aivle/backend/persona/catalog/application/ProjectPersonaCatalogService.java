package com.aivle.backend.persona.catalog.application;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.persona.catalog.BaselinePersonaCatalog;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import com.aivle.backend.persona.catalog.entity.ProjectPersonaSelection;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import com.aivle.backend.persona.catalog.repository.ClusterPersonaPolicyRepository;
import com.aivle.backend.persona.catalog.repository.ProjectPersonaSelectionRepository;
import com.aivle.backend.persona.recommendation.repository.PersonaRecommendationRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ProjectPersonaCatalogService {
    private final ServicePolicyService servicePolicy;
    private final ClusterPersonaPolicyRepository policies;
    private final ProjectPersonaSelectionRepository selections;
    private final BaselinePersonaRepository personas;
    private final PersonaRecommendationRepository recommendations;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final DomainAuditService audits;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AvailablePersonasResponse available(Long userId, Long projectId) {
        User user = currentUser(userId);
        Project project = ownedProject(projectId, user.getId());
        if (!servicePolicy.isClusterPersonaEnabled()) {
            return new AvailablePersonasResponse(false, List.of(), null);
        }
        return response(project);
    }

    @Transactional
    public AvailablePersonasResponse select(
        Long userId,
        Long projectId,
        Long personaId,
        String requestId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User user = currentUser(userId);
        Project project = ownedProject(projectId, user.getId());
        if (!servicePolicy.isClusterPersonaEnabled()) {
            throw new BusinessException(ErrorCode.CLUSTER_PERSONA_DISABLED);
        }
        BaselinePersona persona = personas.findByIdAndCatalogVersionAndDeletedAtIsNull(
                personaId, BaselinePersonaCatalog.VERSION
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.CLUSTER_PERSONA_NOT_FOUND));
        if (policies.findByPersonaIdAndEnabledTrue(personaId).isEmpty()) {
            throw new BusinessException(ErrorCode.CLUSTER_PERSONA_NOT_ALLOWED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ProjectPersonaSelection selection = selections.findByProjectIdForUpdate(projectId)
            .orElseGet(() -> ProjectPersonaSelection.create(project, persona, user, now));
        Long beforeId = selection.getId() == null ? null : selection.getPersona().getId();
        selection.select(persona, user, now);
        selections.save(selection);
        audits.record(
            user.getId(),
            project.getId(),
            AuditEventType.PROJECT_PERSONA_SELECTED,
            "PROJECT_PERSONA_SELECTION",
            selection.getId(),
            requestId,
            Map.of(
                "before", beforeId == null ? "" : beforeId.toString(),
                "after", persona.getId().toString(),
                "personaId", persona.getId().toString()
            )
        );
        return response(project);
    }

    private AvailablePersonasResponse response(Project project) {
        ProjectPersonaSelection selection = selections.findByProjectId(project.getId())
            .orElse(null);
        Long selectedPersonaId = selection == null ? null : selection.getPersona().getId();
        String recommendedCode = recommendations
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                project.getId(), project.getOwner().getId()
            )
            .map(value -> value.getPrimaryPersonaCode())
            .orElse(null);
        List<AvailablePersonaItem> items = policies.findByEnabledTrueOrderByDisplayOrderAsc()
            .stream()
            .filter(policy -> !policy.getPersona().isDeleted())
            .filter(policy -> BaselinePersonaCatalog.VERSION.equals(
                policy.getPersona().getCatalogVersion()
            ))
            .map(policy -> item(
                policy.getPersona(),
                recommendedCode,
                selectedPersonaId,
                true
            ))
            .limit(6)
            .toList();
        AvailablePersonaItem unavailable = null;
        if (selection != null
            && items.stream().noneMatch(item -> item.id().equals(selectedPersonaId))) {
            unavailable = item(
                selection.getPersona(), recommendedCode, selectedPersonaId, false
            );
        }
        return new AvailablePersonasResponse(true, items, unavailable);
    }

    private AvailablePersonaItem item(
        BaselinePersona persona,
        String recommendedCode,
        Long selectedPersonaId,
        boolean available
    ) {
        return new AvailablePersonaItem(
            persona.getId(),
            persona.getDisplayName(),
            persona.getDescription(),
            keywords(persona.getKeyTraitsJson()),
            persona.getPersonaCode().equals(recommendedCode),
            persona.getId().equals(selectedPersonaId),
            available
        );
    }

    private User currentUser(Long userId) {
        return users.findByIdAndDeletedAtIsNull(userId)
            .filter(User::canLogin)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
    }

    private Project ownedProject(Long projectId, Long userId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_PERSONA_SELECTION_NOT_ALLOWED));
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

    public record AvailablePersonasResponse(
        boolean enabled,
        List<AvailablePersonaItem> items,
        AvailablePersonaItem selectedUnavailable
    ) { }

    public record AvailablePersonaItem(
        Long id,
        String name,
        String summary,
        List<String> keywords,
        boolean recommended,
        boolean selected,
        boolean available
    ) { }
}
