package com.aivle.backend.pipeline.marketseed.domain;

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
@Table(name = "market_analysis_seed_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketAnalysisSeedSnapshot extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "selection_id") private Long selectionId;
    @Column(name = "concept_id", length = 64) private String conceptId;
    @Column(name = "source_type", nullable = false, length = 40) private String sourceType;
    @Column(name = "portfolio_selection_id") private Long portfolioSelectionId;
    @Column(name = "portfolio_concept_id", length = 64) private String portfolioConceptId;
    @Column(name = "legal_report_id", length = 64) private String legalReportId;
    @Column(name = "stale_at") private Instant staleAt;
    @Column(name = "schema_version", nullable = false, length = 20) private String schemaVersion;
    @Column(name = "source_snapshot_hash", nullable = false, length = 71) private String sourceSnapshotHash;
    @Column(name = "snapshot_hash", nullable = false, length = 71) private String snapshotHash;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "finalized_at", nullable = false) private Instant finalizedAt;

    public static MarketAnalysisSeedSnapshot create(String id, Long projectId, Long selectionId, String conceptId,
            String schemaVersion, String sourceSnapshotHash, String snapshotHash, String snapshotJson,
            Long createdByUserId, Instant finalizedAt) {
        if (blank(id) || projectId == null || selectionId == null || blank(conceptId) || blank(schemaVersion)
            || !hash(sourceSnapshotHash) || !hash(snapshotHash) || blank(snapshotJson)
            || createdByUserId == null || finalizedAt == null) {
            throw new IllegalArgumentException("시장분석 Seed Snapshot 필드가 올바르지 않습니다.");
        }
        MarketAnalysisSeedSnapshot value = new MarketAnalysisSeedSnapshot();
        value.id = id;
        value.projectId = projectId;
        value.selectionId = selectionId;
        value.conceptId = conceptId;
        value.sourceType = "LEGACY";
        value.schemaVersion = schemaVersion;
        value.sourceSnapshotHash = sourceSnapshotHash;
        value.snapshotHash = snapshotHash;
        value.snapshotJson = snapshotJson;
        value.createdByUserId = createdByUserId;
        value.finalizedAt = finalizedAt;
        return value;
    }

    public static MarketAnalysisSeedSnapshot createPortfolio(String id, Long projectId,
            Long portfolioSelectionId, String portfolioConceptId, String legalReportId,
            String schemaVersion, String sourceSnapshotHash, String snapshotHash,
            String snapshotJson, Long createdByUserId, Instant finalizedAt) {
        if (blank(id) || projectId == null || portfolioSelectionId == null || blank(portfolioConceptId)
                || blank(legalReportId) || blank(schemaVersion) || !hash(sourceSnapshotHash)
                || !hash(snapshotHash) || blank(snapshotJson) || createdByUserId == null || finalizedAt == null) {
            throw new IllegalArgumentException("V2 시장분석 Seed Snapshot 필드가 올바르지 않습니다.");
        }
        MarketAnalysisSeedSnapshot value = new MarketAnalysisSeedSnapshot();
        value.id = id; value.projectId = projectId; value.sourceType = "CONCEPT_PORTFOLIO_V2";
        value.portfolioSelectionId = portfolioSelectionId; value.portfolioConceptId = portfolioConceptId;
        value.legalReportId = legalReportId; value.schemaVersion = schemaVersion;
        value.sourceSnapshotHash = sourceSnapshotHash; value.snapshotHash = snapshotHash;
        value.snapshotJson = snapshotJson; value.createdByUserId = createdByUserId;
        value.finalizedAt = finalizedAt;
        return value;
    }

    public void markStale(Instant now) {
        if (staleAt == null) staleAt = now;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
