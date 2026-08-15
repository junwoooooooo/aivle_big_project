package com.aivle.backend.pipeline.conceptportfolio.selection.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_portfolio_hypothesis_decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptPortfolioHypothesisDecision extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false) private Long selectionId;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false, length = 64) private String conceptId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private PortfolioHypothesisType hypothesisType;
    @Column(nullable = false, columnDefinition = "TEXT") private String proposedValueJson;
    @Column(columnDefinition = "TEXT") private String finalValueJson;
    @Column(nullable = false, length = 40) private String source;
    @Column(nullable = false, length = 40) private String decisionStatus;
    @Column(nullable = false) private int proposalVersion;
    @Column(nullable = false) private boolean locked;
    @Column(nullable = false, length = 30) private String semanticStatus;
    @Column(columnDefinition = "TEXT") private String semanticReason;
    @Column(nullable = false, length = 40) private String legalImpact;
    @Column(nullable = false, length = 40) private String legalReviewStatus;
    @Column(nullable = false) private boolean deltaLegalRequired;
    private Long decidedByUserId;
    private Instant decidedAt;

    public static ConceptPortfolioHypothesisDecision create(Long selectionId, Long projectId, String conceptId,
            PortfolioHypothesisType type, String proposed, String finalValue, String source, String decision,
            int proposalVersion, boolean locked, String semanticStatus, String semanticReason,
            String legalImpact, String legalReviewStatus, boolean deltaRequired, Long userId, Instant decidedAt) {
        ConceptPortfolioHypothesisDecision value = new ConceptPortfolioHypothesisDecision();
        value.id = UUID.randomUUID().toString(); value.selectionId = selectionId; value.projectId = projectId;
        value.conceptId = conceptId; value.hypothesisType = type; value.proposedValueJson = proposed;
        value.apply(finalValue, source, decision, locked, semanticStatus, semanticReason, legalImpact,
            legalReviewStatus, deltaRequired, userId, decidedAt); value.proposalVersion = proposalVersion;
        return value;
    }

    public void apply(String finalValue, String source, String decision, boolean locked,
            String semanticStatus, String semanticReason, String legalImpact, String legalReviewStatus,
            boolean deltaRequired, Long userId, Instant decidedAt) {
        this.finalValueJson = finalValue; this.source = source; this.decisionStatus = decision;
        this.locked = locked; this.semanticStatus = semanticStatus; this.semanticReason = semanticReason;
        this.legalImpact = legalImpact; this.legalReviewStatus = legalReviewStatus;
        this.deltaLegalRequired = deltaRequired; this.decidedByUserId = userId; this.decidedAt = decidedAt;
    }

    public boolean ready() {
        return ("ACCEPTED".equals(decisionStatus) || "USER_EDITED_ACCEPTED".equals(decisionStatus))
            && finalValueJson != null && "VALID".equals(semanticStatus)
            && (!deltaLegalRequired || java.util.Set.of("IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS", "PASSED")
                .contains(legalReviewStatus));
    }
}
