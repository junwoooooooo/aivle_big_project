package com.aivle.backend.pipeline.finance.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "financial_input_preparations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialInputPreparation extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "source_tech_ops_snapshot_id", length = 64) private String sourceTechOpsSnapshotId;
    @Column(name = "source_market_seed_snapshot_id", length = 64) private String sourceMarketSeedSnapshotId;
    @Column(name = "source_market_research_run_id") private Long sourceMarketResearchRunId;
    @Column(name = "source_snapshot_hash", nullable = false, length = 71) private String sourceSnapshotHash;
    @Column(name = "financial_fields_json", nullable = false, columnDefinition = "TEXT") private String financialFieldsJson;
    @Column(name = "upstream_references_json", nullable = false, columnDefinition = "TEXT") private String upstreamReferencesJson;
    @Column(name = "assistance_json", nullable = false, columnDefinition = "TEXT") private String assistanceJson;
    @Column(nullable = false) private int revision;
    @Column(name = "updated_by_user_id", nullable = false) private Long updatedByUserId;

    public static FinancialInputPreparation create(String id, Long projectId, String techOpsSnapshotId,
            String marketSeedSnapshotId, String sourceHash, String fieldsJson, String referencesJson,
            String assistanceJson, Long userId) {
        if (blank(id) || projectId == null || blank(techOpsSnapshotId) || blank(marketSeedSnapshotId)
                || !hash(sourceHash) || blank(fieldsJson) || blank(referencesJson) || blank(assistanceJson)
                || userId == null) throw new IllegalArgumentException("재무 입력 준비값이 올바르지 않습니다.");
        FinancialInputPreparation value = new FinancialInputPreparation();
        value.id = id;
        value.projectId = projectId;
        value.sourceTechOpsSnapshotId = techOpsSnapshotId;
        value.sourceMarketSeedSnapshotId = marketSeedSnapshotId;
        value.sourceSnapshotHash = sourceHash;
        value.financialFieldsJson = fieldsJson;
        value.upstreamReferencesJson = referencesJson;
        value.assistanceJson = assistanceJson;
        value.revision = 1;
        value.updatedByUserId = userId;
        return value;
    }

    public static FinancialInputPreparation createFromBusinessModel(String id, Long projectId, Long businessModelRunId,
            String sourceHash, String fieldsJson, String referencesJson, String assistanceJson, Long userId) {
        if (blank(id) || projectId == null || businessModelRunId == null || !hash(sourceHash)
                || blank(fieldsJson) || blank(referencesJson) || blank(assistanceJson) || userId == null)
            throw new IllegalArgumentException("Financial preparation requires a completed business-model source.");
        FinancialInputPreparation value = new FinancialInputPreparation();
        value.id = id; value.projectId = projectId; value.sourceMarketResearchRunId = businessModelRunId;
        value.sourceSnapshotHash = sourceHash; value.financialFieldsJson = fieldsJson;
        value.upstreamReferencesJson = referencesJson; value.assistanceJson = assistanceJson;
        value.revision = 1; value.updatedByUserId = userId;
        return value;
    }

    public void updateFinancialFields(String json, Long userId) {
        if (blank(json) || userId == null) throw new IllegalArgumentException("재무 입력값이 올바르지 않습니다.");
        financialFieldsJson = json;
        updatedByUserId = userId;
        revision++;
    }

    public void updateAssistance(String json, Long userId) {
        if (blank(json) || userId == null) throw new IllegalArgumentException("재무 AI 추정값이 올바르지 않습니다.");
        assistanceJson = json;
        updatedByUserId = userId;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
