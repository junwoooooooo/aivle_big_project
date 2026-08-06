package com.aivle.backend.pipeline.idea.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_briefs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaBrief extends BaseEntity {
    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdeaBriefStatus status;

    @Column(nullable = false)
    private long briefSequence;

    @Column(length = 64)
    private String parentBriefId;

    @Column(length = 64)
    private String activeTaskRunId;

    @Column(length = 64)
    private String confirmedSnapshotId;

    @Column(length = 71)
    private String snapshotHash;

    @Column(nullable = false)
    private Long createdByUserId;

    @Column(length = 30)
    private String lastCommand;

    @Column(length = 100)
    private String lastIdempotencyKey;

    @Column(length = 71)
    private String lastRequestHash;

    @ElementCollection
    @CollectionTable(name = "idea_brief_attachments", joinColumns = @JoinColumn(name = "brief_id"))
    @Column(name = "stored_file_id", nullable = false)
    private Set<Long> attachmentFileIds = new LinkedHashSet<>();

    public static IdeaBrief initial(Project project, Long userId) {
        return draft(project, userId, 1L, null, null);
    }

    public static IdeaBrief nextDraft(IdeaBrief confirmed, Long userId) {
        if (confirmed.status != IdeaBriefStatus.CONFIRMED) {
            throw new IllegalStateException("draft parent must be a confirmed snapshot");
        }
        return draft(
            confirmed.project,
            userId,
            confirmed.briefSequence + 1,
            confirmed.id,
            confirmed.id
        );
    }

    private static IdeaBrief draft(
        Project project,
        Long userId,
        long sequence,
        String parentBriefId,
        String confirmedSnapshotId
    ) {
        IdeaBrief brief = new IdeaBrief();
        brief.id = UUID.randomUUID().toString();
        brief.project = project;
        brief.status = IdeaBriefStatus.DRAFT;
        brief.briefSequence = sequence;
        brief.parentBriefId = parentBriefId;
        brief.confirmedSnapshotId = confirmedSnapshotId;
        brief.createdByUserId = userId;
        return brief;
    }

    public boolean replay(String command, String idempotencyKey, String requestHash) {
        if (!idempotencyKey.equals(lastIdempotencyKey)) return false;
        if (!command.equals(lastCommand) || !requestHash.equals(lastRequestHash)) {
            throw new IllegalStateException("idempotency key reused with a different request");
        }
        return true;
    }

    public void recordCommand(String command, String idempotencyKey, String requestHash) {
        this.lastCommand = command;
        this.lastIdempotencyKey = idempotencyKey;
        this.lastRequestHash = requestHash;
    }

    public void startDeriving(String taskRunId, String idempotencyKey, String requestHash) {
        requireMutable();
        this.status = IdeaBriefStatus.DERIVING;
        this.activeTaskRunId = taskRunId;
        recordCommand("DERIVE", idempotencyKey, requestHash);
    }

    public void needsInput() {
        requireMutable();
        this.status = IdeaBriefStatus.NEEDS_INPUT;
        this.activeTaskRunId = null;
    }

    public void readyForReview() {
        requireMutable();
        this.status = IdeaBriefStatus.READY_FOR_REVIEW;
        this.activeTaskRunId = null;
    }

    public void failDerivation() {
        requireMutable();
        this.status = IdeaBriefStatus.FAILED;
        this.activeTaskRunId = null;
    }

    public void confirm(String snapshotHash, String idempotencyKey, String requestHash) {
        if (status != IdeaBriefStatus.READY_FOR_REVIEW) {
            throw new IllegalStateException("idea brief is not ready for confirmation");
        }
        if (snapshotHash == null || !snapshotHash.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("snapshot hash is invalid");
        }
        this.status = IdeaBriefStatus.CONFIRMED;
        this.snapshotHash = snapshotHash;
        this.confirmedSnapshotId = id;
        this.activeTaskRunId = null;
        recordCommand("CONFIRM", idempotencyKey, requestHash);
    }

    public void replaceAttachments(Set<Long> storedFileIds) {
        requireMutable();
        attachmentFileIds.clear();
        attachmentFileIds.addAll(storedFileIds == null ? Set.of() : storedFileIds);
    }

    public void markDraft() {
        requireMutable();
        this.status = IdeaBriefStatus.DRAFT;
        this.activeTaskRunId = null;
    }

    public boolean isConfirmed() {
        return status == IdeaBriefStatus.CONFIRMED;
    }

    public void requireMutable() {
        if (isConfirmed()) throw new IllegalStateException("confirmed idea brief snapshot is immutable");
    }
}
