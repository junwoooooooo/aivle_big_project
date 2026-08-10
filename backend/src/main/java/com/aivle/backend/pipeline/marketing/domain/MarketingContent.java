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

    public static MarketingContent queued(String id, Long projectId, String marketingSourceSnapshotId,
            String sourceHash, String sourceJson, String requestJson, MarketingContentType type,
            String channel, String title, Long userId) {
        MarketingContent value = new MarketingContent();
        value.id = id; value.projectId = projectId; value.marketingSourceSnapshotId = marketingSourceSnapshotId;
        value.sourceSnapshotHash = sourceHash; value.sourceSnapshotJson = sourceJson;
        value.requestJson = requestJson; value.contentType = type; value.channel = channel;
        value.title = title; value.status = MarketingContentStatus.QUEUED;
        value.createdByUserId = userId; return value;
    }

    public void attachTaskRun(String id) { taskRunId = id; }
    public void start() { if (status != MarketingContentStatus.QUEUED) throw new IllegalStateException("content is not queued"); status = MarketingContentStatus.RUNNING; }
    public int completeRevision() { if (status != MarketingContentStatus.RUNNING) throw new IllegalStateException("content is not running"); status = MarketingContentStatus.COMPLETED; return ++currentRevisionNumber; }
    public int addUserRevision() { if (status != MarketingContentStatus.COMPLETED) throw new IllegalStateException("content is not editable"); return ++currentRevisionNumber; }
    public void fail() { if (status != MarketingContentStatus.FINALIZED) status = MarketingContentStatus.FAILED; }
    public void regenerate(String snapshotId, String sourceHash, String sourceJson, String request, String taskId) {
        if (status == MarketingContentStatus.FINALIZED || status == MarketingContentStatus.RUNNING || status == MarketingContentStatus.QUEUED)
            throw new IllegalStateException("content cannot be regenerated");
        marketingSourceSnapshotId = snapshotId; sourceSnapshotHash = sourceHash; sourceSnapshotJson = sourceJson; requestJson = request;
        taskRunId = taskId; status = MarketingContentStatus.QUEUED;
    }
    public int finalizeContent(Instant at) {
        if (status != MarketingContentStatus.COMPLETED) throw new IllegalStateException("content is not finalizable");
        status = MarketingContentStatus.FINALIZED; finalizedRevisionNumber = ++currentRevisionNumber; finalizedAt = at;
        return finalizedRevisionNumber;
    }
}
