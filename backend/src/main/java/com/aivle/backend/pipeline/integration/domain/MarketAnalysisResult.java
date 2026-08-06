package com.aivle.backend.pipeline.integration.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "module_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketAnalysisResult extends BaseEntity {
    @Id @Column(name = "module_run_id", length = 64) private String moduleRunId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "input_snapshot_id", nullable = false, length = 64) private String inputSnapshotId;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "result_reference", nullable = false, length = 1000) private String resultReference;
    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT") private String summaryJson;
    @Column(name = "competitors_json", nullable = false, columnDefinition = "TEXT") private String competitorsJson;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;
    @Column(name = "result_hash", nullable = false, length = 71) private String resultHash;

    public static MarketAnalysisResult received(String runId, Long projectId, String snapshotId, String status,
            String reference, String summaryJson, String competitorsJson, Instant completedAt, String hash) {
        MarketAnalysisResult value = new MarketAnalysisResult();
        value.moduleRunId = runId; value.projectId = projectId; value.inputSnapshotId = snapshotId;
        value.status = status; value.resultReference = reference; value.summaryJson = summaryJson;
        value.competitorsJson = competitorsJson; value.completedAt = completedAt; value.resultHash = hash;
        return value;
    }
}
