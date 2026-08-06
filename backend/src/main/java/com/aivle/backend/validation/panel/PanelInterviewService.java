package com.aivle.backend.validation.panel;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.validation.PersonaValidationSourceService;
import com.aivle.backend.validation.PersonaValidationSourceService.Context;
import com.aivle.backend.validation.PersonaValidationTypes.InterviewPurpose;
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
public class PanelInterviewService {
    private final PanelInterviewRepository interviews;
    private final PersonaValidationSourceService sources;
    private final PanelInterviewSimulationService simulation;
    private final ServicePolicyService servicePolicy;
    private final DomainAuditService audits;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SummaryResponse> list(Long userId, Long projectId) {
        Context context = sources.context(userId, projectId, List.of());
        return interviews.findAllByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                context.project().getId()
            ).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public DetailResponse detail(Long userId, Long projectId, Long interviewId) {
        sources.context(userId, projectId, List.of());
        return detail(interview(projectId, interviewId));
    }

    @Transactional
    public DetailResponse create(Long userId, Long projectId, Command command, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Validated validated = validate(command);
        Context context = sources.context(userId, projectId, validated.personaIds());
        PanelInterview interview = interviews.save(PanelInterview.create(
            context.project(),
            context.actor(),
            validated.title(),
            validated.purpose(),
            json(validated.personaIds()),
            json(validated.questions())
        ));
        audit(context, interview, AuditEventType.PANEL_INTERVIEW_CREATED, requestId);
        return detail(interview);
    }

    @Transactional
    public DetailResponse update(
        Long userId,
        Long projectId,
        Long interviewId,
        Command command,
        String requestId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Validated validated = validate(command);
        Context context = sources.context(userId, projectId, validated.personaIds());
        PanelInterview interview = interview(projectId, interviewId);
        interview.updateDraft(
            validated.title(),
            validated.purpose(),
            json(validated.personaIds()),
            json(validated.questions())
        );
        audit(context, interview, AuditEventType.PANEL_INTERVIEW_UPDATED, requestId);
        return detail(interview);
    }

    @Transactional
    public DetailResponse run(Long userId, Long projectId, Long interviewId, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        PanelInterview interview = interview(projectId, interviewId);
        List<Long> personaIds = longList(interview.getPersonaIdsJson());
        List<String> questions = stringList(interview.getQuestionsJson());
        Context context = sources.context(userId, projectId, personaIds);
        PanelInterviewSimulationService.SimulationResult result =
            simulation.simulate(context, interview.getPurpose(), questions);
        Map<String, Object> snapshot = new LinkedHashMap<>(context.sourceBase());
        snapshot.put("personas", context.selected());
        snapshot.put("purpose", interview.getPurpose().name());
        snapshot.put("questions", questions);
        interview.complete(
            json(result.answers()),
            json(result.summary()),
            json(snapshot),
            LocalDateTime.now(clock)
        );
        audit(context, interview, AuditEventType.PANEL_INTERVIEW_COMPLETED, requestId);
        return detail(interview);
    }

    @Transactional
    public void delete(Long userId, Long projectId, Long interviewId, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Context context = sources.context(userId, projectId, List.of());
        PanelInterview interview = interview(projectId, interviewId);
        interview.softDelete(LocalDateTime.now(clock));
        audit(context, interview, AuditEventType.PANEL_INTERVIEW_DELETED, requestId);
    }

    private Validated validate(Command command) {
        if (command == null || command.purpose() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        List<Long> personaIds = command.personaIds() == null
            ? List.of() : command.personaIds().stream().filter(Objects::nonNull).distinct().toList();
        if (personaIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PANEL_INTERVIEW_INVALID_PERSONA);
        }
        if (personaIds.size() > 3) {
            throw new BusinessException(ErrorCode.PANEL_INTERVIEW_PERSONA_LIMIT_EXCEEDED);
        }
        List<String> questions = command.questions() == null ? List.of()
            : command.questions().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (questions.size() < 3) {
            throw new BusinessException(ErrorCode.PANEL_INTERVIEW_QUESTION_REQUIRED);
        }
        if (questions.size() > 10 || questions.stream().anyMatch(value -> value.length() > 300)) {
            throw new BusinessException(ErrorCode.PANEL_INTERVIEW_QUESTION_LIMIT_EXCEEDED);
        }
        return new Validated(required(command.title(), 200), command.purpose(), personaIds, questions);
    }

    private PanelInterview interview(Long projectId, Long interviewId) {
        return interviews.findByIdAndProjectIdAndDeletedAtIsNull(interviewId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PANEL_INTERVIEW_NOT_FOUND));
    }

    private SummaryResponse summary(PanelInterview value) {
        return new SummaryResponse(
            value.getId(),
            value.getTitle(),
            value.getPurpose(),
            value.getStatus(),
            longList(value.getPersonaIdsJson()).size(),
            stringList(value.getQuestionsJson()).size(),
            value.getUpdatedAt(),
            value.getCompletedAt()
        );
    }

    private DetailResponse detail(PanelInterview value) {
        return new DetailResponse(
            summary(value),
            longList(value.getPersonaIdsJson()),
            stringList(value.getQuestionsJson()),
            object(value.getAnswersJson(), List.of()),
            object(value.getSummaryJson(), Map.of()),
            object(value.getSourceSnapshotJson(), Map.of()),
            "이 결과는 프로젝트 정보와 페르소나 특성을 바탕으로 생성한 예상 반응이며, 실제 고객 조사 결과를 대체하지 않습니다."
        );
    }

    private void audit(
        Context context,
        PanelInterview interview,
        AuditEventType type,
        String requestId
    ) {
        audits.record(
            context.actor().getId(),
            context.project().getId(),
            type,
            "PANEL_INTERVIEW",
            interview.getId(),
            requestId,
            Map.of("panelInterviewId", interview.getId().toString())
        );
    }

    private String required(String value, int max) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.PANEL_INTERVIEW_RUN_FAILED);
        }
    }

    private List<Long> longList(String json) {
        try {
            return Arrays.asList(objectMapper.readValue(json, Long[].class));
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private List<String> stringList(String json) {
        try {
            return Arrays.asList(objectMapper.readValue(json, String[].class));
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private Object object(String json, Object fallback) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JacksonException exception) {
            return fallback;
        }
    }

    private record Validated(
        String title,
        InterviewPurpose purpose,
        List<Long> personaIds,
        List<String> questions
    ) { }

    public record Command(
        String title,
        InterviewPurpose purpose,
        List<Long> personaIds,
        List<String> questions
    ) { }
    public record SummaryResponse(
        Long id,
        String title,
        InterviewPurpose purpose,
        com.aivle.backend.validation.PersonaValidationTypes.ValidationStatus status,
        int personaCount,
        int questionCount,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
    ) { }
    public record DetailResponse(
        SummaryResponse interview,
        List<Long> personaIds,
        List<String> questions,
        Object answers,
        Object summary,
        Object sourceSnapshot,
        String disclaimer
    ) { }
}
