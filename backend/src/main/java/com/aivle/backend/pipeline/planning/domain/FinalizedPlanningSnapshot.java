package com.aivle.backend.pipeline.planning.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name = "finalized_planning_snapshots")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalizedPlanningSnapshot extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "planning_snapshot_id", nullable = false, length = 64) private String planningSnapshotId;
    @Column(name = "source_selection_snapshot_id", nullable = false, length = 64) private String sourceSelectionSnapshotId;
    @Column(name = "sequence_number", nullable = false) private int sequence;
    @Column(name = "parent_snapshot_id", length = 64) private String parentSnapshotId;
    @Column(name = "display_label", nullable = false, length = 300) private String displayLabel;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(name = "snapshot_hash", nullable = false, length = 71) private String snapshotHash;
    @Column(name = "finalized_by_user_id", nullable = false) private Long finalizedByUserId;
    @Column(name = "finalized_at", nullable = false) private Instant finalizedAt;

    public static FinalizedPlanningSnapshot create(String id, Long projectId, String planningId, String sourceId,
            int sequence, String parentId, String label, String json, String hash, Long userId, Instant at) {
        FinalizedPlanningSnapshot value = new FinalizedPlanningSnapshot(); value.id=id; value.projectId=projectId;
        value.planningSnapshotId=planningId; value.sourceSelectionSnapshotId=sourceId; value.sequence=sequence;
        value.parentSnapshotId=parentId; value.displayLabel=label; value.snapshotJson=json; value.snapshotHash=hash;
        value.finalizedByUserId=userId; value.finalizedAt=at; return value;
    }
}
