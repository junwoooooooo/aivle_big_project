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

    public static ConceptAttempt begin(ConceptSlot slot, ConceptAttemptPhase phase, String taskRunId) {
        ConceptAttempt attempt = new ConceptAttempt();
        attempt.id = UUID.randomUUID().toString();
        attempt.slot = slot;
        attempt.projectId = slot.getProjectId();
        attempt.attemptNumber = slot.beginAttempt(phase);
        attempt.phase = phase;
        attempt.taskRunId = taskRunId;
        return attempt;
    }
}
