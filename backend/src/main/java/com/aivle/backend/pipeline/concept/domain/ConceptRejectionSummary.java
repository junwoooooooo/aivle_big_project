package com.aivle.backend.pipeline.concept.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_rejection_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptRejectionSummary extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "slot_id", nullable = false) private ConceptSlot slot;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "attempt_id", length = 64) private String attemptId;
    @Column(nullable = false, length = 80) private String reasonCode;
    @Column(nullable = false, length = 500) private String safeSummary;

    public static ConceptRejectionSummary create(ConceptSlot slot, String attemptId, String reasonCode, String safeSummary) {
        ConceptRejectionSummary value = new ConceptRejectionSummary();
        value.slot = slot;
        value.projectId = slot.getProjectId();
        value.attemptId = attemptId;
        value.reasonCode = reasonCode;
        value.safeSummary = safeSummary;
        return value;
    }
}
