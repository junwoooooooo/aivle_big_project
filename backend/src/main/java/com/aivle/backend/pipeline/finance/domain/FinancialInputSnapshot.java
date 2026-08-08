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
    @Column(name = "source_tech_ops_snapshot_id", nullable = false, length = 64) private String sourceTechOpsSnapshotId;
    @Column(name = "source_market_seed_snapshot_id", nullable = false, length = 64) private String sourceMarketSeedSnapshotId;
    @Column(name = "schema_version", nullable = false, length = 20) private String schemaVersion;
    @Column(name = "snapshot_hash", nullable = false, length = 71) private String snapshotHash;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "finalized_at", nullable = false) private Instant finalizedAt;

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

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
