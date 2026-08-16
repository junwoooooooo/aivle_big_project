package com.aivle.backend.pipeline.launchreadiness.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "launch_readiness_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaunchReadinessReport extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Enumerated(EnumType.STRING) @Column(name = "module_type", nullable = false, length = 24) private ModuleType moduleType;
    @Column(name = "input_snapshot_id", nullable = false, length = 64) private String inputSnapshotId;
    @Column(name = "task_run_id", nullable = false, unique = true, length = 64) private String taskRunId;
    @Column(name = "result_schema_version", nullable = false, length = 20) private String resultSchemaVersion;
    @Column(name = "analysis_json", nullable = false, columnDefinition = "TEXT") private String analysisJson;
    @Column(name = "quality_json", nullable = false, columnDefinition = "TEXT") private String qualityJson;
    @Column(name = "external_evidence_json", nullable = false, columnDefinition = "TEXT") private String externalEvidenceJson;
    @Column(name = "result_hash", nullable = false, length = 71) private String resultHash;
    @Column(name = "is_current", nullable = false) private boolean current;
    @Column(nullable = false) private boolean stale;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;

    public static LaunchReadinessReport create(String id, Long projectId, ModuleType moduleType,
            String snapshotId, String taskRunId, String analysis, String quality, String evidence,
            String resultHash, Long userId, Instant now) {
        LaunchReadinessReport value = new LaunchReadinessReport();
        value.id = id; value.projectId = projectId; value.moduleType = moduleType;
        value.inputSnapshotId = snapshotId; value.taskRunId = taskRunId; value.resultSchemaVersion = "1.0";
        value.analysisJson = analysis; value.qualityJson = quality; value.externalEvidenceJson = evidence;
        value.resultHash = resultHash; value.current = true; value.stale = false;
        value.completedAt = now; value.createdByUserId = userId;
        return value;
    }

    public void supersede() { current = false; stale = true; }
}
