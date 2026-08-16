package com.aivle.backend.pipeline.finance.domain;

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
@Table(name = "financial_input_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialInputSnapshot extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "preparation_id", nullable = false, length = 64) private String preparationId;
    @Column(name = "source_tech_ops_snapshot_id", length = 64) private String sourceTechOpsSnapshotId;
    @Column(name = "source_market_seed_snapshot_id", length = 64) private String sourceMarketSeedSnapshotId;
    @Column(name = "source_market_research_version_id") private Long sourceMarketResearchVersionId;
    @Column(name = "source_business_model_version_id") private Long sourceBusinessModelVersionId;
    @Column(name = "schema_version", nullable = false, length = 20) private String schemaVersion;
    @Column(name = "snapshot_hash", nullable = false, length = 71) private String snapshotHash;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "finalized_at", nullable = false) private Instant finalizedAt;
    @Column(name = "source_mode", length = 40) private String sourceMode;
    @Column(name = "preparation_revision") private Integer preparationRevision;
    @Column(name = "source_document_artifact_id", length = 64) private String sourceDocumentArtifactId;
    @Column(name = "source_document_hash", length = 71) private String sourceDocumentHash;

    public static FinancialInputSnapshot createFromUserDocument(String id, Long projectId,
            String preparationId, int preparationRevision, String artifactId, String documentHash,
            String schemaVersion, String hash, String json, Long userId, Instant finalizedAt) {
        if (blank(id) || projectId == null || blank(preparationId) || preparationRevision < 1
                || blank(artifactId) || !hash(documentHash) || blank(schemaVersion) || !hash(hash)
                || blank(json) || userId == null || finalizedAt == null) {
            throw new IllegalArgumentException("사용자 재무 입력 Snapshot 정보가 올바르지 않습니다.");
        }
        FinancialInputSnapshot value = new FinancialInputSnapshot();
        value.id = id; value.projectId = projectId; value.preparationId = preparationId;
        value.sourceMode = "USER_DOCUMENT_INPUT"; value.preparationRevision = preparationRevision;
        value.sourceDocumentArtifactId = artifactId; value.sourceDocumentHash = documentHash;
        value.schemaVersion = schemaVersion; value.snapshotHash = hash; value.snapshotJson = json;
        value.createdByUserId = userId; value.finalizedAt = finalizedAt;
        return value;
    }

    public static FinancialInputSnapshot create(String id, Long projectId, String preparationId,
            String techOpsSnapshotId, String marketSeedSnapshotId, String schemaVersion, String hash,
            String json, Long userId, Instant finalizedAt) {
        if (blank(id) || projectId == null || blank(preparationId) || blank(techOpsSnapshotId)
                || blank(marketSeedSnapshotId) || blank(schemaVersion) || !hash(hash) || blank(json)
                || userId == null || finalizedAt == null)
            throw new IllegalArgumentException("재무 입력 Snapshot이 올바르지 않습니다.");
        FinancialInputSnapshot value = new FinancialInputSnapshot();
        value.id = id;
        value.projectId = projectId;
        value.preparationId = preparationId;
        value.sourceTechOpsSnapshotId = techOpsSnapshotId;
        value.sourceMarketSeedSnapshotId = marketSeedSnapshotId;
        value.schemaVersion = schemaVersion;
        value.snapshotHash = hash;
        value.snapshotJson = json;
        value.createdByUserId = userId;
        value.finalizedAt = finalizedAt;
        return value;
    }

    public static FinancialInputSnapshot createFromMarketAndBusinessModel(String id, Long projectId,
            String preparationId, Long marketVersionId, Long businessModelVersionId,
            String schemaVersion, String hash, String json, Long userId, Instant finalizedAt) {
        if (blank(id) || projectId == null || blank(preparationId) || marketVersionId == null
                || businessModelVersionId == null || blank(schemaVersion) || !hash(hash) || blank(json)
                || userId == null || finalizedAt == null) {
            throw new IllegalArgumentException("재무 Snapshot에는 current Market/BM source가 필요합니다.");
        }
        FinancialInputSnapshot value = new FinancialInputSnapshot();
        value.id = id;
        value.projectId = projectId;
        value.preparationId = preparationId;
        value.sourceMarketResearchVersionId = marketVersionId;
        value.sourceBusinessModelVersionId = businessModelVersionId;
        value.schemaVersion = schemaVersion;
        value.snapshotHash = hash;
        value.snapshotJson = json;
        value.createdByUserId = userId;
        value.finalizedAt = finalizedAt;
        return value;
    }

    public static FinancialInputSnapshot create(String id, Long projectId, String preparationId,
            String techOpsSnapshotId, String marketSeedSnapshotId, Long marketVersionId,
            Long businessModelVersionId, String schemaVersion, String hash,
            String json, Long userId, Instant finalizedAt) {
        FinancialInputSnapshot value = create(id, projectId, preparationId, techOpsSnapshotId,
            marketSeedSnapshotId, schemaVersion, hash, json, userId, finalizedAt);
        if (marketVersionId == null || businessModelVersionId == null)
            throw new IllegalArgumentException("재무 Snapshot에는 Market/BM version이 필요합니다.");
        value.sourceMarketResearchVersionId = marketVersionId;
        value.sourceBusinessModelVersionId = businessModelVersionId;
        return value;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
