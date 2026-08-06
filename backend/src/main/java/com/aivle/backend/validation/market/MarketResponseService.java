package com.aivle.backend.validation.market;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.validation.PersonaValidationSourceService;
import com.aivle.backend.validation.PersonaValidationSourceService.Context;
import com.aivle.backend.validation.market.MarketResponseScoringService.MessageVariant;
import com.aivle.backend.validation.panel.PanelInterview;
import com.aivle.backend.validation.panel.PanelInterviewRepository;
import com.aivle.backend.validation.PersonaValidationTypes.ValidationStatus;
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
public class MarketResponseService {
    private final MarketResponseRepository responses;
    private final PanelInterviewRepository interviews;
    private final PersonaValidationSourceService sources;
    private final MarketResponseScoringService scoring;
    private final ServicePolicyService servicePolicy;
    private final DomainAuditService audits;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SummaryResponse> list(Long userId, Long projectId) {
        Context context = sources.context(userId, projectId, List.of());
        return responses.findAllByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                context.project().getId()
            ).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public DetailResponse detail(Long userId, Long projectId, Long responseId) {
        sources.context(userId, projectId, List.of());
        return detail(response(projectId, responseId));
    }

    @Transactional
    public DetailResponse create(Long userId, Long projectId, Command command, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Validated validated = validate(command);
        Context context = marketContext(userId, projectId, validated.personaIds());
        PanelInterview interview = panel(projectId, validated.panelInterviewId());
        MarketResponsePrediction response = responses.save(MarketResponsePrediction.create(
            context.project(),
            context.actor(),
            interview,
            validated.title(),
            json(validated.personaIds()),
            json(validated.messages()),
            validated.priceContext(),
            validated.primaryChannel()
        ));
        audit(context, response, AuditEventType.MARKET_RESPONSE_CREATED, requestId);
        return detail(response);
    }

    @Transactional
    public DetailResponse update(
        Long userId,
        Long projectId,
        Long responseId,
        Command command,
        String requestId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Validated validated = validate(command);
        Context context = marketContext(userId, projectId, validated.personaIds());
        PanelInterview interview = panel(projectId, validated.panelInterviewId());
        MarketResponsePrediction response = response(projectId, responseId);
        response.updateDraft(
            interview,
            validated.title(),
            json(validated.personaIds()),
            json(validated.messages()),
            validated.priceContext(),
            validated.primaryChannel()
        );
        audit(context, response, AuditEventType.MARKET_RESPONSE_UPDATED, requestId);
        return detail(response);
    }

    @Transactional
    public DetailResponse run(Long userId, Long projectId, Long responseId, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        MarketResponsePrediction response = response(projectId, responseId);
        List<Long> personaIds = longList(response.getPersonaIdsJson());
        Context context = marketContext(userId, projectId, personaIds);
        List<MessageVariant> messages = messages(response.getMessageVariantsJson());
        MarketResponseScoringService.ScoringResult result = scoring.score(
            context,
            messages,
            response.getPriceContext(),
            response.getPrimaryChannel(),
            response.getPanelInterview() != null
        );
        Map<String, Object> snapshot = new LinkedHashMap<>(context.sourceBase());
        snapshot.put("personas", context.selected());
        snapshot.put("messages", messages);
        snapshot.put("priceContext", Optional.ofNullable(response.getPriceContext()).orElse(""));
        snapshot.put("primaryChannel", Optional.ofNullable(response.getPrimaryChannel()).orElse(""));
        if (response.getPanelInterview() != null) {
            snapshot.put("panelInterview", Map.of(
                "id", response.getPanelInterview().getId(),
                "summary", object(response.getPanelInterview().getSummaryJson(), Map.of())
            ));
        }
        response.complete(
            json(result.results()),
            json(result.summary()),
            json(snapshot),
            LocalDateTime.now(clock)
        );
        audit(context, response, AuditEventType.MARKET_RESPONSE_COMPLETED, requestId);
        return detail(response);
    }

    @Transactional
    public void delete(Long userId, Long projectId, Long responseId, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Context context = marketContext(userId, projectId, List.of());
        MarketResponsePrediction response = response(projectId, responseId);
        response.softDelete(LocalDateTime.now(clock));
        audit(context, response, AuditEventType.MARKET_RESPONSE_DELETED, requestId);
    }

    private Context marketContext(Long userId, Long projectId, List<Long> ids) {
        try {
            return sources.context(userId, projectId, ids);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.PANEL_INTERVIEW_INVALID_PERSONA) {
                throw new BusinessException(ErrorCode.MARKET_RESPONSE_INVALID_PERSONA);
            }
            throw exception;
        }
    }

    private Validated validate(Command command) {
        if (command == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        List<Long> personaIds = command.personaIds() == null ? List.of()
            : command.personaIds().stream().filter(Objects::nonNull).distinct().toList();
        if (personaIds.isEmpty() || personaIds.size() > 3) {
            throw new BusinessException(ErrorCode.MARKET_RESPONSE_INVALID_PERSONA);
        }
        List<MessageVariant> messages = command.messages() == null ? List.of()
            : command.messages().stream()
                .filter(Objects::nonNull)
                .map(value -> new MessageVariant(
                    required(value.id(), 10),
                    required(value.text(), 300)
                ))
                .toList();
        if (messages.isEmpty()) {
            throw new BusinessException(ErrorCode.MARKET_RESPONSE_MESSAGE_REQUIRED);
        }
        if (messages.size() > 3) {
            throw new BusinessException(ErrorCode.MARKET_RESPONSE_MESSAGE_LIMIT_EXCEEDED);
        }
        if (messages.stream().map(MessageVariant::id).distinct().count() != messages.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return new Validated(
            required(command.title(), 200),
            personaIds,
            messages,
            trimmed(command.priceContext(), 300),
            trimmed(command.primaryChannel(), 80),
            command.panelInterviewId()
        );
    }

    private PanelInterview panel(Long projectId, Long panelInterviewId) {
        if (panelInterviewId == null) return null;
        PanelInterview interview = interviews.findByIdAndProjectIdAndDeletedAtIsNull(
                panelInterviewId, projectId
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.PANEL_INTERVIEW_NOT_FOUND));
        if (interview.getStatus() != ValidationStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.PANEL_INTERVIEW_NOT_FOUND);
        }
        return interview;
    }

    private MarketResponsePrediction response(Long projectId, Long responseId) {
        return responses.findByIdAndProjectIdAndDeletedAtIsNull(responseId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_RESPONSE_NOT_FOUND));
    }

    private SummaryResponse summary(MarketResponsePrediction value) {
        return new SummaryResponse(
            value.getId(),
            value.getTitle(),
            value.getStatus(),
            longList(value.getPersonaIdsJson()).size(),
            messages(value.getMessageVariantsJson()).size(),
            value.getPanelInterview() == null ? null : value.getPanelInterview().getId(),
            value.getUpdatedAt(),
            value.getCompletedAt()
        );
    }

    private DetailResponse detail(MarketResponsePrediction value) {
        return new DetailResponse(
            summary(value),
            longList(value.getPersonaIdsJson()),
            messages(value.getMessageVariantsJson()),
            value.getPriceContext(),
            value.getPrimaryChannel(),
            object(value.getResultJson(), List.of()),
            object(value.getSummaryJson(), Map.of()),
            object(value.getSourceSnapshotJson(), Map.of()),
            "점수는 실제 시장 확률이 아니라 Persona 특성과 프로젝트 검증 결과를 비교하기 위한 상대 지표입니다."
        );
    }

    private void audit(
        Context context,
        MarketResponsePrediction response,
        AuditEventType type,
        String requestId
    ) {
        audits.record(
            context.actor().getId(),
            context.project().getId(),
            type,
            "MARKET_RESPONSE",
            response.getId(),
            requestId,
            Map.of("marketResponseId", response.getId().toString())
        );
    }

    private String required(String value, int max) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String trimmed(String value, int max) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.MARKET_RESPONSE_RUN_FAILED);
        }
    }

    private List<Long> longList(String json) {
        try {
            return Arrays.asList(objectMapper.readValue(json, Long[].class));
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private List<MessageVariant> messages(String json) {
        try {
            return Arrays.asList(objectMapper.readValue(json, MessageVariant[].class));
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
        List<Long> personaIds,
        List<MessageVariant> messages,
        String priceContext,
        String primaryChannel,
        Long panelInterviewId
    ) { }

    public record Command(
        String title,
        List<Long> personaIds,
        List<MessageVariant> messages,
        String priceContext,
        String primaryChannel,
        Long panelInterviewId
    ) { }
    public record SummaryResponse(
        Long id,
        String title,
        com.aivle.backend.validation.PersonaValidationTypes.ValidationStatus status,
        int personaCount,
        int messageCount,
        Long panelInterviewId,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
    ) { }
    public record DetailResponse(
        SummaryResponse prediction,
        List<Long> personaIds,
        List<MessageVariant> messages,
        String priceContext,
        String primaryChannel,
        Object results,
        Object summary,
        Object sourceSnapshot,
        String disclaimer
    ) { }
}
