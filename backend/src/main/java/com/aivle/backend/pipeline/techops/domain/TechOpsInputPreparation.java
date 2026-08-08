package com.aivle.backend.pipeline.techops.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tech_ops_input_preparations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechOpsInputPreparation extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "source_market_seed_snapshot_id", nullable = false, length = 64) private String sourceMarketSeedSnapshotId;
    @Column(name = "source_snapshot_hash", nullable = false, length = 71) private String sourceSnapshotHash;
    @Column(name = "required_facts_json", nullable = false, columnDefinition = "TEXT") private String requiredFactsJson;
    @Column(name = "proposal_decisions_json", nullable = false, columnDefinition = "TEXT") private String proposalDecisionsJson;
    @Column(nullable = false) private int revision;
    @Column(name = "updated_by_user_id", nullable = false) private Long updatedByUserId;

    public static TechOpsInputPreparation create(String id, Long projectId, String sourceId, String sourceHash,
            String requiredFactsJson, String proposalDecisionsJson, Long userId) {
        if (blank(id) || projectId == null || blank(sourceId) || !hash(sourceHash) || blank(requiredFactsJson)
                || blank(proposalDecisionsJson) || userId == null) throw new IllegalArgumentException("기술·운영 준비값이 올바르지 않습니다.");
        TechOpsInputPreparation value = new TechOpsInputPreparation();
        value.id = id; value.projectId = projectId; value.sourceMarketSeedSnapshotId = sourceId;
        value.sourceSnapshotHash = sourceHash; value.requiredFactsJson = requiredFactsJson;
        value.proposalDecisionsJson = proposalDecisionsJson; value.revision = 1; value.updatedByUserId = userId;
        return value;
    }

    public void updateRequiredFacts(String json, Long userId) {
        if (blank(json) || userId == null) throw new IllegalArgumentException("기술·운영 사용자 입력이 올바르지 않습니다.");
        requiredFactsJson = json; updatedByUserId = userId; revision++;
    }

    public void updateProposalDecisions(String json, Long userId) {
        if (blank(json) || userId == null) throw new IllegalArgumentException("기술·운영 제안 결정이 올바르지 않습니다.");
        proposalDecisionsJson = json; updatedByUserId = userId; revision++;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
