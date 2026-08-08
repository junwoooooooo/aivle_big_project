package com.aivle.backend.pipeline.selection.domain;

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
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_hypothesis_decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptHypothesisDecision extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selection_id", nullable = false) private ConceptSelection selection;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "concept_id", nullable = false, length = 64) private String conceptId;
    @Enumerated(EnumType.STRING) @Column(name = "hypothesis_type", nullable = false, length = 40)
    private HypothesisType hypothesisType;
    @Column(name = "proposed_value_json", nullable = false, columnDefinition = "TEXT") private String proposedValueJson;
    @Column(nullable = false, length = 30) private String source;
    @Enumerated(EnumType.STRING) @Column(name = "decision_status", nullable = false, length = 40)
    private HypothesisDecisionStatus decisionStatus;
    @Column(name = "final_value_json", columnDefinition = "TEXT") private String finalValueJson;
    @Column(name = "proposal_version", nullable = false) private int proposalVersion;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "decided_at") private Instant decidedAt;
    @Enumerated(EnumType.STRING) @Column(name = "legal_impact", nullable = false, length = 30)
    private HypothesisLegalImpact legalImpact;
    @Enumerated(EnumType.STRING) @Column(name = "legal_review_status", nullable = false, length = 30)
    private HypothesisLegalReviewStatus legalReviewStatus;
    @Column(name = "legal_review_result_json", columnDefinition = "TEXT") private String legalReviewResultJson;
    @Column(nullable = false) private boolean locked;

    public static ConceptHypothesisDecision initial(ConceptSelection selection, HypothesisType type,
            String valueJson, String source, boolean locked, Long userId, Instant now) {
        ConceptHypothesisDecision value = create(selection, type, valueJson, source, 1, userId);
        value.locked = locked;
        if (locked) {
            value.decisionStatus = HypothesisDecisionStatus.ACCEPTED;
            value.finalValueJson = valueJson;
            value.decidedAt = now;
        } else {
            value.decisionStatus = HypothesisDecisionStatus.PROPOSED;
        }
        return value;
    }

    public static ConceptHypothesisDecision alternative(ConceptHypothesisDecision previous,
            String proposedValueJson, Long userId) {
        if (previous.locked || previous.decisionStatus != HypothesisDecisionStatus.REJECTED) {
            throw new IllegalStateException("only a rejected open hypothesis can receive an alternative");
        }
        ConceptHypothesisDecision value = create(previous.selection, previous.hypothesisType,
            proposedValueJson, "AI_HYPOTHESIS", previous.proposalVersion + 1, userId);
        value.decisionStatus = HypothesisDecisionStatus.ALTERNATIVE_PROPOSED;
        return value;
    }

    private static ConceptHypothesisDecision create(ConceptSelection selection, HypothesisType type,
            String valueJson, String source, int version, Long userId) {
        if (selection == null || type == null || valueJson == null || valueJson.isBlank()
            || source == null || source.isBlank() || userId == null) {
            throw new IllegalArgumentException("hypothesis decision fields are required");
        }
        ConceptHypothesisDecision value = new ConceptHypothesisDecision();
        value.id = UUID.randomUUID().toString();
        value.selection = selection;
        value.projectId = selection.getProjectId();
        value.conceptId = selection.getConceptId();
        value.hypothesisType = type;
        value.proposedValueJson = valueJson;
        value.source = source;
        value.proposalVersion = version;
        value.userId = userId;
        value.legalImpact = type.legalSensitive()
            ? HypothesisLegalImpact.LEGAL_SENSITIVE : HypothesisLegalImpact.NON_LEGAL;
        value.legalReviewStatus = HypothesisLegalReviewStatus.NOT_REQUIRED;
        return value;
    }

    public void reject() {
        requireOpen();
        decisionStatus = HypothesisDecisionStatus.REJECTED;
        finalValueJson = null;
        decidedAt = null;
    }

    public void accept(String finalValueJson, boolean edited, Long decidingUserId, Instant now,
            boolean baselineChanged, boolean legalPassed, String legalResultJson) {
        requireOpen();
        if (finalValueJson == null || finalValueJson.isBlank()) throw new IllegalArgumentException("final value is required");
        if (legalImpact == HypothesisLegalImpact.LEGAL_SENSITIVE && baselineChanged && !legalPassed) {
            legalReviewStatus = HypothesisLegalReviewStatus.FAILED;
            legalReviewResultJson = legalResultJson;
            return;
        }
        this.finalValueJson = finalValueJson;
        this.userId = decidingUserId;
        this.decidedAt = now;
        this.decisionStatus = edited ? HypothesisDecisionStatus.USER_EDITED_ACCEPTED : HypothesisDecisionStatus.ACCEPTED;
        this.legalReviewStatus = legalImpact == HypothesisLegalImpact.LEGAL_SENSITIVE && baselineChanged
            ? HypothesisLegalReviewStatus.PASSED : HypothesisLegalReviewStatus.NOT_REQUIRED;
        this.legalReviewResultJson = legalResultJson;
    }

    public boolean accepted() {
        return decisionStatus == HypothesisDecisionStatus.ACCEPTED
            || decisionStatus == HypothesisDecisionStatus.USER_EDITED_ACCEPTED;
    }

    private void requireOpen() {
        if (locked) throw new IllegalStateException("locked hypothesis cannot be mutated");
        if (accepted()) throw new IllegalStateException("accepted hypothesis cannot be mutated");
    }
}
