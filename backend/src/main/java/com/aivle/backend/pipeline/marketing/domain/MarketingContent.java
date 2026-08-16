package com.aivle.backend.pipeline.marketing.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "pipeline_marketing_contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketingContent extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "marketing_source_snapshot_id", nullable = false, length = 64) private String marketingSourceSnapshotId;
    @Column(name = "source_snapshot_hash", nullable = false, length = 71) private String sourceSnapshotHash;
    @Column(name = "source_snapshot_json", nullable = false, columnDefinition = "TEXT") private String sourceSnapshotJson;
    @Column(name = "request_json", nullable = false, columnDefinition = "TEXT") private String requestJson;
    @Enumerated(EnumType.STRING) @Column(name = "content_type", nullable = false, length = 30) private MarketingContentType contentType;
    @Column(nullable = false, length = 120) private String channel;
    @Column(nullable = false, length = 200) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private MarketingContentStatus status;
    @Column(name = "task_run_id", length = 64) private String taskRunId;
    @Column(name = "current_revision_number", nullable = false) private int currentRevisionNumber;
    @Column(name = "finalized_revision_number") private Integer finalizedRevisionNumber;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "finalized_at") private Instant finalizedAt;
    @Column(nullable = false) private int attempt;
    @Column(name = "previous_content_id", length = 64) private String previousContentId;

    public static MarketingContent queued(String id, Long projectId, String marketingSourceSnapshotId,
            String sourceHash, String sourceJson, String requestJson, MarketingContentType type,
            String channel, String title, Long userId, int attempt, String previousContentId) {
        if (attempt < 1 || attempt > 3) throw new IllegalArgumentException("marketing attempt is out of range");
        MarketingContent value = new MarketingContent();
        value.id = id; value.projectId = projectId; value.marketingSourceSnapshotId = marketingSourceSnapshotId;
        value.sourceSnapshotHash = sourceHash; value.sourceSnapshotJson = sourceJson;
        value.requestJson = requestJson; value.contentType = type; value.channel = channel;
        value.title = title; value.status = MarketingContentStatus.QUEUED;
        value.createdByUserId = userId; value.attempt = attempt;
        value.previousContentId = previousContentId; return value;
    }

    public void attachTaskRun(String id) { taskRunId = id; }
    public void start() { if (status != MarketingContentStatus.QUEUED) throw new IllegalStateException("content is not queued"); status = MarketingContentStatus.RUNNING; }
    public int completeRevision() { if (status != MarketingContentStatus.RUNNING) throw new IllegalStateException("content is not running"); status = MarketingContentStatus.COMPLETED; return ++currentRevisionNumber; }
    public int addUserRevision() { if (status != MarketingContentStatus.COMPLETED) throw new IllegalStateException("content is not editable"); return ++currentRevisionNumber; }
    public void fail() { if (status != MarketingContentStatus.FINALIZED) status = MarketingContentStatus.FAILED; }
    public void markStale() { if (status != MarketingContentStatus.STALE) status = MarketingContentStatus.STALE; }
    public int finalizeContent(Instant at) {
        if (status != MarketingContentStatus.COMPLETED) throw new IllegalStateException("content is not finalizable");
        status = MarketingContentStatus.FINALIZED; finalizedRevisionNumber = ++currentRevisionNumber; finalizedAt = at;
        return finalizedRevisionNumber;
    }
}
