package com.aivle.backend.pipeline.launchreadiness.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "launch_readiness_input_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaunchReadinessInputSnapshot extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Enumerated(EnumType.STRING) @Column(name = "module_type", nullable = false, length = 24) private ModuleType moduleType;
    @Column(name = "source_document_artifact_id", nullable = false, length = 64) private String sourceDocumentArtifactId;
    @Column(name = "source_document_hash", nullable = false, length = 71) private String sourceDocumentHash;
    @Column(name = "source_document_name", nullable = false) private String sourceDocumentName;
    @Column(name = "source_mode", nullable = false, length = 40) private String sourceMode;
    @Column(name = "input_schema_version", nullable = false, length = 20) private String inputSchemaVersion;
    @Column(name = "parsed_input_json", nullable = false, columnDefinition = "TEXT") private String parsedInputJson;
    @Column(name = "snapshot_hash", nullable = false, length = 71) private String snapshotHash;
    @Column(name = "is_current", nullable = false) private boolean current;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "finalized_at", nullable = false) private Instant finalizedAt;

    public enum ModuleType { TECHNOLOGY, OPERATIONS }

    public static LaunchReadinessInputSnapshot create(String id, Long projectId, ModuleType moduleType,
            String artifactId, String documentHash, String documentName, String parsedInputJson,
            String snapshotHash, Long userId, Instant now) {
        LaunchReadinessInputSnapshot value = new LaunchReadinessInputSnapshot();
        value.id = id; value.projectId = projectId; value.moduleType = moduleType;
        value.sourceDocumentArtifactId = artifactId; value.sourceDocumentHash = documentHash;
        value.sourceDocumentName = documentName; value.sourceMode = "USER_DOCUMENT_INPUT";
        value.inputSchemaVersion = "1.0"; value.parsedInputJson = parsedInputJson;
        value.snapshotHash = snapshotHash; value.current = true; value.createdByUserId = userId;
        value.finalizedAt = now;
        return value;
    }

    public void supersede() { current = false; }
}
