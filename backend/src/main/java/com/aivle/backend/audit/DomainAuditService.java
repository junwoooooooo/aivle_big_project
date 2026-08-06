package com.aivle.backend.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DomainAuditService {
    private static final Set<String> ALLOWED_METADATA_KEYS = Set.of(
        "status",
        "fieldCode",
        "previousStatus",
        "newStatus",
        "safeErrorCode",
        "jobId",
        "structuredPlanId",
        "feasibilityAssessmentId",
        "recommendationId",
        "primaryPersonaCode",
        "legalReviewId",
        "assessmentId",
        "resultStatus",
        "verdict",
        "overallRiskLevel",
        "reason",
        "before",
        "after",
        "targetUserId",
        "settingKey",
        "personaId",
        "marketingContentId",
        "versionNumber",
        "panelInterviewId",
        "marketResponseId",
        "sourceSnapshotVersion",
        "financialAnalysisId",
        "scenarioCount",
        "periodMonths",
        "sourceFeasibilityAssessmentId"
    );

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock jobClock;

    public void record(
        Long actorUserId,
        Long projectId,
        AuditEventType eventType,
        String aggregateType,
        Long aggregateId,
        String requestId,
        Map<String, String> metadata
    ) {
        if (!ALLOWED_METADATA_KEYS.containsAll(metadata.keySet())) {
            throw new IllegalArgumentException("audit metadata key is not allowed");
        }
        repository.save(AuditEvent.record(
            actorUserId,
            projectId,
            eventType,
            aggregateType,
            aggregateId,
            normalizeRequestId(requestId),
            serialize(metadata),
            LocalDateTime.now(jobClock)
        ));
    }

    private String serialize(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw new IllegalStateException("safe audit metadata serialization failed", exception);
        }
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        String normalized = requestId.trim();
        return normalized.length() <= 100
            ? normalized
            : normalized.substring(0, 100);
    }
}
