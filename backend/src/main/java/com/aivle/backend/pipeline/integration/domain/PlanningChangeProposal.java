package com.aivle.backend.pipeline.integration.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "planning_change_proposals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanningChangeProposal extends BaseEntity {
    @Id @Column(length = 100) private String id;
    @Column(name = "module_run_id", nullable = false, length = 64) private String moduleRunId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "meaningful_title", nullable = false, length = 300) private String meaningfulTitle;
    @Column(name = "affected_fields_json", nullable = false, columnDefinition = "TEXT") private String affectedFieldsJson;
    @Column(name = "before_json", nullable = false, columnDefinition = "TEXT") private String beforeJson;
    @Column(name = "after_json", nullable = false, columnDefinition = "TEXT") private String afterJson;
    @Column(nullable = false, length = 2000) private String reason;
    @Column(name = "evidence_references_json", nullable = false, columnDefinition = "TEXT") private String evidenceReferencesJson;
    @Column(name = "impact_areas_json", nullable = false, columnDefinition = "TEXT") private String impactAreasJson;
    @Enumerated(EnumType.STRING) @Column(name = "decision_status", nullable = false, length = 30) private ProposalDecisionStatus decisionStatus;
    @Column(name = "modified_after_json", columnDefinition = "TEXT") private String modifiedAfterJson;

    public static PlanningChangeProposal pending(String id, String runId, Long projectId, String title,
            String fields, String before, String after, String reason, String evidence, String impacts) {
        PlanningChangeProposal value = new PlanningChangeProposal();
        value.id = id; value.moduleRunId = runId; value.projectId = projectId; value.meaningfulTitle = title;
        value.affectedFieldsJson = fields; value.beforeJson = before; value.afterJson = after; value.reason = reason;
        value.evidenceReferencesJson = evidence; value.impactAreasJson = impacts;
        value.decisionStatus = ProposalDecisionStatus.PENDING;
        return value;
    }

    public void decide(ProposalDecisionStatus decision, String modifiedAfterJson) {
        if (decision == ProposalDecisionStatus.PENDING) throw new IllegalArgumentException("PENDING is not a user action");
        if (decision == ProposalDecisionStatus.PARTIALLY_ADOPT && (modifiedAfterJson == null || modifiedAfterJson.isBlank()))
            throw new IllegalArgumentException("modifiedAfter is required for partial adoption");
        if (decision != ProposalDecisionStatus.PARTIALLY_ADOPT && modifiedAfterJson != null)
            throw new IllegalArgumentException("modifiedAfter is only allowed for partial adoption");
        this.decisionStatus = decision;
        this.modifiedAfterJson = modifiedAfterJson;
    }
}
