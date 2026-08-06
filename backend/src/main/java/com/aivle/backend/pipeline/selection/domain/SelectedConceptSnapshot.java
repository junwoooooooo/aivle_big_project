package com.aivle.backend.pipeline.selection.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "selected_concept_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SelectedConceptSnapshot extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "selection_id", nullable = false) private ConceptSelection selection;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "concept_id", nullable = false, length = 64) private String conceptId;
    @Column(name = "sequence_number", nullable = false) private int sequence;
    @Column(name = "parent_snapshot_id", length = 64) private String parentSnapshotId;
    @Column(name = "source_concept_hash", nullable = false, length = 71) private String sourceConceptHash;
    @Column(name = "snapshot_hash", nullable = false, length = 71) private String snapshotHash;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "selected_at", nullable = false) private Instant selectedAt;

    public static SelectedConceptSnapshot create(String snapshotId, ConceptSelection selection, int sequence, String parentSnapshotId,
                                                  String sourceConceptHash, String snapshotHash, String snapshotJson,
                                                  Long userId, Instant selectedAt) {
        if (sequence < 1) throw new IllegalArgumentException("snapshot sequence must be positive");
        if (snapshotHash == null || !snapshotHash.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("snapshot hash is invalid");
        SelectedConceptSnapshot value = new SelectedConceptSnapshot();
        value.id = snapshotId;
        value.selection = selection;
        value.projectId = selection.getProjectId();
        value.conceptId = selection.getConceptId();
        value.sequence = sequence;
        value.parentSnapshotId = parentSnapshotId;
        value.sourceConceptHash = sourceConceptHash;
        value.snapshotHash = snapshotHash;
        value.snapshotJson = snapshotJson;
        value.createdByUserId = userId;
        value.selectedAt = selectedAt;
        return value;
    }
}
