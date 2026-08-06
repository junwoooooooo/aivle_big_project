package com.aivle.backend.validation;

import com.aivle.backend.analysis.feasibility.repository.FeasibilityAssessmentRepository;
import com.aivle.backend.analysis.legal.repository.LegalReviewRepository;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.persona.catalog.BaselinePersonaCatalog;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import com.aivle.backend.persona.catalog.repository.ClusterPersonaPolicyRepository;
import com.aivle.backend.persona.catalog.repository.ProjectPersonaSelectionRepository;
import com.aivle.backend.persona.recommendation.repository.PersonaRecommendationRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PersonaValidationSourceService {
    private final UserRepository users;
    private final ProjectRepository projects;
    private final ClusterPersonaPolicyRepository policies;
    private final ProjectPersonaSelectionRepository selections;
    private final PersonaRecommendationRepository recommendations;
    private final LegalReviewRepository legalReviews;
    private final FeasibilityAssessmentRepository feasibility;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Context context(Long userId, Long projectId, List<Long> requestedIds) {
        User actor = users.findByIdAndDeletedAtIsNull(userId)
            .filter(User::canLogin)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        Project project = projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, actor.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        List<PersonaView> candidates = candidates(project);
        Map<Long, PersonaView> byId = new LinkedHashMap<>();
        candidates.forEach(value -> byId.put(value.id(), value));
        List<Long> ids = requestedIds == null ? List.of() : requestedIds.stream().distinct().toList();
        List<PersonaView> selected = ids.stream().map(byId::get).toList();
        if (selected.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.PANEL_INTERVIEW_INVALID_PERSONA);
        }
        return new Context(actor, project, candidates, selected, snapshotBase(project));
    }

    @Transactional(readOnly = true)
    public CandidateResponse candidates(Long userId, Long projectId) {
        Context context = context(userId, projectId, List.of());
        return new CandidateResponse(context.candidates());
    }

    private List<PersonaView> candidates(Project project) {
        LinkedHashMap<Long, PersonaView> values = new LinkedHashMap<>();
        String recommendedCode = recommendations
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                project.getId(), project.getOwner().getId()
            )
            .map(value -> value.getPrimaryPersonaCode())
            .orElse(null);
        Long selectedId = selections.findByProjectId(project.getId())
            .map(value -> value.getPersona().getId())
            .orElse(null);
        policies.findByEnabledTrueOrderByDisplayOrderAsc().stream()
            .map(value -> value.getPersona())
            .filter(this::current)
            .forEach(value -> values.put(value.getId(), view(value, recommendedCode, selectedId, true)));
        return List.copyOf(values.values());
    }

    private boolean current(BaselinePersona value) {
        return !value.isDeleted()
            && BaselinePersonaCatalog.VERSION.equals(value.getCatalogVersion());
    }

    private PersonaView view(
        BaselinePersona persona,
        String recommendedCode,
        Long selectedId,
        boolean available
    ) {
        return new PersonaView(
            persona.getId(),
            persona.getPersonaCode(),
            persona.getDisplayName(),
            persona.getDescription(),
            stringList(persona.getKeyTraitsJson()).stream().limit(5).toList(),
            persona.getPersonaCode().equals(recommendedCode),
            persona.getId().equals(selectedId),
            available
        );
    }

    private Map<String, Object> snapshotBase(Project project) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("project", Map.of(
            "id", project.getId(),
            "title", project.getTitle(),
            "summary", Optional.ofNullable(project.getDescription()).orElse(""),
            "industryCategory", Optional.ofNullable(project.getIndustryCategory()).orElse("")
        ));
        feasibility
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                project.getId(), project.getOwner().getId()
            )
            .ifPresent(result -> value.put("feasibility", Map.of(
                "summary", Optional.ofNullable(result.getSummary()).orElse(""),
                "available", true
            )));
        legalReviews
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                project.getId(), project.getOwner().getId()
            )
            .ifPresent(result -> value.put("legalReview", Map.of(
                "summary", Optional.ofNullable(result.getSummary()).orElse(""),
                "riskLevel", result.getRiskLevel() == null ? "" : result.getRiskLevel().name(),
                "available", true
            )));
        value.put("capturedAt", LocalDateTime.now(clock).toString());
        return value;
    }

    private List<String> stringList(String json) {
        try {
            return Arrays.stream(objectMapper.readValue(json, String[].class))
                .filter(value -> value != null && !value.isBlank())
                .toList();
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    public record Context(
        User actor,
        Project project,
        List<PersonaView> candidates,
        List<PersonaView> selected,
        Map<String, Object> sourceBase
    ) { }

    public record CandidateResponse(List<PersonaView> items) { }

    public record PersonaView(
        Long id,
        String code,
        String name,
        String summary,
        List<String> keyTraits,
        boolean recommended,
        boolean selected,
        boolean available
    ) { }
}
