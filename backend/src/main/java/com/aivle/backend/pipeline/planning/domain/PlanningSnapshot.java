package com.aivle.backend.pipeline.planning.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name = "planning_snapshots")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanningSnapshot extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "source_selection_snapshot_id", nullable = false, length = 64) private String sourceSelectionSnapshotId;
    @Column(name = "sequence_number", nullable = false) private int sequence;
    @Column(name = "parent_snapshot_id", length = 64) private String parentSnapshotId;
    @Column(name = "display_label", nullable = false, length = 300) private String displayLabel;
    @Column(name = "planning_json", nullable = false, columnDefinition = "TEXT") private String planningJson;
    @Column(name = "snapshot_hash", nullable = false, length = 71) private String snapshotHash;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "snapshotted_at", nullable = false) private Instant snapshottedAt;

    public static PlanningSnapshot create(String id, Long projectId, String sourceId, int sequence, String parentId,
            String label, String json, String hash, Long userId, Instant at) {
        PlanningSnapshot value = new PlanningSnapshot(); value.id=id; value.projectId=projectId;
        value.sourceSelectionSnapshotId=sourceId; value.sequence=sequence; value.parentSnapshotId=parentId;
        value.displayLabel=label; value.planningJson=json; value.snapshotHash=hash; value.createdByUserId=userId; value.snapshottedAt=at;
        return value;
    }
}
