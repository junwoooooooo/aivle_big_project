package com.aivle.backend.pipeline.concept.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptAttempt extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "slot_id", nullable = false) private ConceptSlot slot;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false) private int attemptNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ConceptAttemptPhase phase;
    @Column(length = 64) private String taskRunId;
    @Enumerated(EnumType.STRING) @Column(length = 40) private ConceptAttemptError errorClassification;
    @Column(length = 80) private String safeErrorCode;
    @Column(nullable = false) private boolean retryable;
    @Column(columnDefinition = "TEXT") private String resultJson;

    public static ConceptAttempt begin(ConceptSlot slot, ConceptAttemptPhase phase, String taskRunId) {
        return create(slot, phase, taskRunId, slot.beginAttempt(phase));
    }

    public static ConceptAttempt retry(ConceptSlot slot, ConceptAttemptPhase phase, String taskRunId) {
        return create(slot, phase, taskRunId, slot.beginRetry());
    }

    private static ConceptAttempt create(ConceptSlot slot, ConceptAttemptPhase phase, String taskRunId, int attemptNumber) {
        ConceptAttempt attempt = new ConceptAttempt();
        attempt.id = UUID.randomUUID().toString();
        attempt.slot = slot;
        attempt.projectId = slot.getProjectId();
        attempt.attemptNumber = attemptNumber;
        attempt.phase = phase;
        attempt.taskRunId = taskRunId;
        return attempt;
    }

    public void succeed(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) throw new IllegalArgumentException("attempt result is required");
        this.resultJson = resultJson;
        this.errorClassification = null;
        this.safeErrorCode = null;
        this.retryable = false;
    }

    public void fail(ConceptAttemptError classification, String safeErrorCode, boolean retryable) {
        this.errorClassification = classification;
        this.safeErrorCode = safeErrorCode;
        this.retryable = retryable;
        this.resultJson = null;
    }

    public void reject(ConceptAttemptError classification, String safeErrorCode, String candidateJson) {
        if (candidateJson == null || candidateJson.isBlank()) throw new IllegalArgumentException("candidate result is required");
        this.errorClassification = classification;
        this.safeErrorCode = safeErrorCode;
        this.retryable = false;
        this.resultJson = candidateJson;
    }
}
