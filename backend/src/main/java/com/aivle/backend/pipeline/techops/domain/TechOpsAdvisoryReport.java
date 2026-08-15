package com.aivle.backend.pipeline.techops.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tech_ops_advisory_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechOpsAdvisoryReport extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "task_run_id", nullable = false, length = 64) private String taskRunId;
    @Column(name = "tech_ops_input_snapshot_id", nullable = false, length = 64) private String techOpsInputSnapshotId;
    @Column(name = "source_market_seed_snapshot_id", nullable = false, length = 64) private String sourceMarketSeedSnapshotId;
    @Column(name = "source_market_research_version_id", nullable = false) private Long sourceMarketResearchVersionId;
    @Column(name = "source_business_model_version_id", nullable = false) private Long sourceBusinessModelVersionId;
    @Column(name = "source_portfolio_selection_id", nullable = false) private Long sourcePortfolioSelectionId;
    @Column(name = "selected_concept_id", nullable = false, length = 64) private String selectedConceptId;
    @Column(name = "selected_concept_hash", nullable = false, length = 71) private String selectedConceptHash;
    @Column(name = "contract_version", nullable = false, length = 20) private String contractVersion;
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT") private String resultJson;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;

    public static TechOpsAdvisoryReport create(Long projectId, String taskRunId, String snapshotId,
            String seedId, Long marketVersionId, Long businessModelVersionId, Long selectionId,
            String conceptId, String conceptHash, String contractVersion, String resultJson, Long userId) {
        if (projectId == null || blank(taskRunId) || blank(snapshotId) || blank(seedId)
                || marketVersionId == null || businessModelVersionId == null || selectionId == null
                || blank(conceptId) || conceptHash == null || !conceptHash.matches("sha256:[0-9a-f]{64}")
                || blank(contractVersion) || blank(resultJson) || userId == null) {
            throw new IllegalArgumentException("TechOps Advisory report is invalid");
        }
        TechOpsAdvisoryReport value = new TechOpsAdvisoryReport();
        value.id = UUID.randomUUID().toString(); value.projectId = projectId; value.taskRunId = taskRunId;
        value.techOpsInputSnapshotId = snapshotId; value.sourceMarketSeedSnapshotId = seedId;
        value.sourceMarketResearchVersionId = marketVersionId;
        value.sourceBusinessModelVersionId = businessModelVersionId;
        value.sourcePortfolioSelectionId = selectionId; value.selectedConceptId = conceptId;
        value.selectedConceptHash = conceptHash; value.contractVersion = contractVersion;
        value.resultJson = resultJson; value.createdByUserId = userId;
        return value;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
