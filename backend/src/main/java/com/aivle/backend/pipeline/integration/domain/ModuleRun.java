package com.aivle.backend.pipeline.integration.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "module_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModuleRun extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "handoff_id", nullable = false, length = 64) private String handoffId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ModuleType module;
    @Column(name = "input_snapshot_id", nullable = false, length = 64) private String inputSnapshotId;
    @Column(name = "input_snapshot_hash", nullable = false, length = 71) private String inputSnapshotHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ModuleRunStatus status;
    @Column(name = "external_run_reference", length = 500) private String externalRunReference;
    @Column(name = "cancel_requested", nullable = false) private boolean cancelRequested;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "result_reference", length = 1000) private String resultReference;
    @Column(name = "result_hash", length = 71) private String resultHash;
    @Column(name = "safe_error_code", length = 80) private String safeErrorCode;

    public static ModuleRun notConnected(String id, ModuleHandoff handoff) {
        ModuleRun value = new ModuleRun();
        value.id = id;
        value.handoffId = handoff.getId();
        value.projectId = handoff.getProjectId();
        value.module = handoff.getModule();
        value.inputSnapshotId = handoff.getInputSnapshotId();
        value.inputSnapshotHash = handoff.getInputSnapshotHash();
        value.status = ModuleRunStatus.NOT_CONNECTED;
        return value;
    }

    public void receiveResult(ModuleRunStatus resultStatus, String resultReference, String resultHash, Instant completedAt) {
        if (resultStatus != ModuleRunStatus.COMPLETED && resultStatus != ModuleRunStatus.FAILED
                && resultStatus != ModuleRunStatus.NEEDS_INPUT)
            throw new IllegalArgumentException("unsupported result status");
        this.status = resultStatus;
        this.resultReference = resultReference;
        this.resultHash = resultHash;
        this.completedAt = completedAt;
        this.safeErrorCode = null;
    }
}
