package com.aivle.backend.pipeline.marketing.domain;

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
@Table(name = "marketing_source_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketingSourceSnapshot extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "source_market_seed_snapshot_id", nullable = false, length = 64) private String sourceMarketSeedSnapshotId;
    @Column(name = "selection_id") private Long selectionId;
    @Column(name = "concept_id", length = 64) private String conceptId;
    @Column(name = "source_type", nullable = false, length = 40) private String sourceType;
    @Column(name = "portfolio_selection_id") private Long portfolioSelectionId;
    @Column(name = "portfolio_concept_id", length = 64) private String portfolioConceptId;
    @Column(name = "schema_version", nullable = false, length = 20) private String schemaVersion;
    @Column(name = "snapshot_hash", nullable = false, length = 71) private String snapshotHash;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "finalized_at", nullable = false) private Instant finalizedAt;

    public static MarketingSourceSnapshot create(String id, Long projectId, String marketSeedId, Long selectionId,
            String conceptId, String schemaVersion, String hash, String json, Long userId, Instant finalizedAt) {
        if (blank(id) || projectId == null || blank(marketSeedId) || selectionId == null || blank(conceptId)
            || blank(schemaVersion) || !hash(hash) || blank(json) || userId == null || finalizedAt == null) {
            throw new IllegalArgumentException("Marketing Source Snapshot 필드가 올바르지 않습니다.");
        }
        MarketingSourceSnapshot value = new MarketingSourceSnapshot();
        value.id = id; value.projectId = projectId; value.sourceMarketSeedSnapshotId = marketSeedId;
        value.selectionId = selectionId; value.conceptId = conceptId; value.sourceType = "LEGACY";
        value.schemaVersion = schemaVersion;
        value.snapshotHash = hash; value.snapshotJson = json; value.createdByUserId = userId;
        value.finalizedAt = finalizedAt;
        return value;
    }

    public static MarketingSourceSnapshot createPortfolio(String id, Long projectId, String marketSeedId,
            Long portfolioSelectionId, String portfolioConceptId, String schemaVersion, String hash,
            String json, Long userId, Instant finalizedAt) {
        if (blank(id) || projectId == null || blank(marketSeedId) || portfolioSelectionId == null
                || blank(portfolioConceptId) || blank(schemaVersion) || !hash(hash) || blank(json)
                || userId == null || finalizedAt == null) {
            throw new IllegalArgumentException("V2 Marketing Source Snapshot 필드가 올바르지 않습니다.");
        }
        MarketingSourceSnapshot value = new MarketingSourceSnapshot();
        value.id = id;
        value.projectId = projectId;
        value.sourceMarketSeedSnapshotId = marketSeedId;
        value.sourceType = "CONCEPT_PORTFOLIO_V2";
        value.portfolioSelectionId = portfolioSelectionId;
        value.portfolioConceptId = portfolioConceptId;
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
