package com.aivle.backend.finance.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "financial_analysis_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialAnalysisReport extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "input_snapshot_id", nullable = false, length = 64) private String inputSnapshotId;
    @Column(name = "input_snapshot_hash", nullable = false, length = 71) private String inputSnapshotHash;
    @Column(name = "source_market_research_run_id") private Long sourceMarketResearchRunId;
    @Column(name = "report_json", nullable = false, columnDefinition = "TEXT") private String reportJson;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;

    public static FinancialAnalysisReport create(String id, Long projectId, String snapshotId, String snapshotHash,
            Long businessModelRunId, String reportJson, Long userId, Instant completedAt) {
        FinancialAnalysisReport value = new FinancialAnalysisReport();
        value.id=id; value.projectId=projectId; value.inputSnapshotId=snapshotId; value.inputSnapshotHash=snapshotHash;
        value.sourceMarketResearchRunId=businessModelRunId; value.reportJson=reportJson;
        value.createdByUserId=userId; value.completedAt=completedAt;
        return value;
    }
}
