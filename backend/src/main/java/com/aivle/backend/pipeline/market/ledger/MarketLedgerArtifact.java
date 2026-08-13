package com.aivle.backend.pipeline.market.ledger;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.pipeline.market.MarketResearchVersion;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_research_ledger_artifacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketLedgerArtifact extends BaseEntity {
    public enum State { STAGED, COMMITTED }

    @Id @Column(length = 36) private String id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false, length = 64) private String conceptId;
    @Column(nullable = false, length = 64) private String sourceRunId;
    @Column(nullable = false, length = 64) private String marketTaskRunId;
    @Column(nullable = false, length = 64) private String taskAttemptId;
    @Column(nullable = false, length = 71) private String canonicalInputHash;
    @Column(nullable = false, length = 71) private String conceptSnapshotHash;
    @Column(nullable = false, length = 20) private String asOfDate;
    @Column(nullable = false, length = 1000) private String objectKey;
    @Column(nullable = false, length = 80) private String contentType;
    @Column(nullable = false) private Long sizeBytes;
    @Column(nullable = false, length = 64) private String objectChecksumSha256;
    @Column(nullable = false, length = 64) private String manifestHash;
    @Column(nullable = false, columnDefinition = "TEXT") private String manifestJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "market_research_version_id")
    private MarketResearchVersion marketResearchVersion;
    private LocalDateTime committedAt;

    public static MarketLedgerArtifact staged(String id, Long projectId, String conceptId, String sourceRunId,
            String taskRunId, String attemptId, String inputHash, String conceptHash, String asOf,
            String objectKey, String contentType, long size, String checksum,
            String manifestHash, String manifestJson) {
        MarketLedgerArtifact value = new MarketLedgerArtifact();
        value.id = id;
        value.projectId = projectId;
        value.conceptId = conceptId;
        value.sourceRunId = sourceRunId;
        value.marketTaskRunId = taskRunId;
        value.taskAttemptId = attemptId;
        value.canonicalInputHash = inputHash;
        value.conceptSnapshotHash = conceptHash;
        value.asOfDate = asOf;
        value.objectKey = objectKey;
        value.contentType = contentType;
        value.sizeBytes = size;
        value.objectChecksumSha256 = checksum;
        value.manifestHash = manifestHash;
        value.manifestJson = manifestJson;
        value.state = State.STAGED;
        return value;
    }

    public void commit(MarketResearchVersion version) {
        if (state != State.STAGED || marketResearchVersion != null) {
            throw new IllegalStateException("market ledger artifact is not staged");
        }
        marketResearchVersion = version;
        state = State.COMMITTED;
        committedAt = LocalDateTime.now();
    }
}
