package com.aivle.backend.pipeline.planning.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.pipeline.integration.domain.ProposalDecisionStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name = "planning_change_decisions")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanningChangeDecision extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "proposal_id", nullable = false, length = 100) private String proposalId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ProposalDecisionStatus decision;
    @Column(name = "applied_value_json", columnDefinition = "TEXT") private String appliedValueJson;
    @Column(name = "decided_by_user_id", nullable = false) private Long decidedByUserId;
    @Column(name = "decided_at", nullable = false) private Instant decidedAt;

    public static PlanningChangeDecision decide(String proposalId, Long projectId, ProposalDecisionStatus decision,
            String appliedValueJson, Long userId, Instant at) {
        if (decision == ProposalDecisionStatus.PENDING) throw new IllegalArgumentException("PENDING is not a decision");
        if (decision == ProposalDecisionStatus.PARTIALLY_ADOPT && (appliedValueJson == null || appliedValueJson.isBlank()))
            throw new IllegalArgumentException("partial adoption requires an applied value");
        if (decision != ProposalDecisionStatus.PARTIALLY_ADOPT && appliedValueJson != null)
            throw new IllegalArgumentException("only partial adoption accepts an applied value");
        PlanningChangeDecision value = new PlanningChangeDecision();
        value.proposalId = proposalId; value.projectId = projectId; value.decision = decision;
        value.appliedValueJson = appliedValueJson; value.decidedByUserId = userId; value.decidedAt = at;
        return value;
    }
}
