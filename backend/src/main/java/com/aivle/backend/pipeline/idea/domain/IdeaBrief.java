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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_briefs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaBrief extends BaseEntity {
    public static final int MAX_CLARIFICATION_ROUNDS = 2;
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

    @Column(columnDefinition = "TEXT")
    private String overviewText;

    @Column(length = 64)
    private String activeTaskRunId;

    @Column(length = 64)
    private String confirmedSnapshotId;

    @Column(length = 71)
    private String snapshotHash;

    @Column(columnDefinition = "TEXT")
    private String userFacingSummary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contradictionsJson = "[]";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String missingFieldKeysJson = "[]";

    @Column(length = 30)
    private String aiReadinessStatus;

    @Column(nullable = false)
    private int readinessScore;

    @Column(nullable = false)
    private int clarificationRound;

    @Column(length = 71)
    private String assessmentInputHash;

    @Column(length = 40)
    private String safetyDecision;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String safetyCategoriesJson = "[]";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String safetyRestrictionsJson = "[]";

    @Column(length = 1000)
    private String safetyUserFacingReason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String interpretationJson = "{}";

    private LocalDateTime interpretationConfirmedAt;

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

    public void startClarification(String taskRunId) {
        requireMutable();
        if (clarificationRound >= MAX_CLARIFICATION_ROUNDS) {
            throw new IllegalStateException("clarification round limit reached");
        }
        this.clarificationRound++;
        this.status = IdeaBriefStatus.DERIVING;
        this.activeTaskRunId = taskRunId;
    }

    public void startFinalSynthesis(String taskRunId) {
        requireMutable();
        this.status = IdeaBriefStatus.DERIVING;
        this.activeTaskRunId = taskRunId;
    }

    public void updateOverview(String overview) {
        requireMutable();
        if (overview == null || overview.isBlank() || overview.length() > 20_000) {
            throw new IllegalArgumentException("idea overview is invalid");
        }
        this.overviewText = overview;
    }

    public void applyAssessment(String summary, String contradictionsJson,
            String missingFieldKeysJson, String readinessStatus, int score, String assessmentInputHash) {
        requireMutable();
        if (summary == null || summary.isBlank() || summary.length() > 1_000
            || contradictionsJson == null || missingFieldKeysJson == null
            || !("NEEDS_INPUT".equals(readinessStatus) || "READY_FOR_REVIEW".equals(readinessStatus))
            || score < 0 || score > 100
            || assessmentInputHash == null || !assessmentInputHash.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("idea assessment is invalid");
        }
        this.userFacingSummary = summary;
        this.contradictionsJson = contradictionsJson;
        this.missingFieldKeysJson = missingFieldKeysJson;
        this.aiReadinessStatus = readinessStatus;
        this.readinessScore = score;
        this.assessmentInputHash = assessmentInputHash;
    }

    public void applyAssessment(String summary, String contradictionsJson,
            String missingFieldKeysJson, String readinessStatus, int score) {
        applyAssessment(summary, contradictionsJson, missingFieldKeysJson, readinessStatus, score,
            "sha256:" + "0".repeat(64));
    }

    public void needsInput(int unansweredQuestionCount, int requiredMissingFieldCount) {
        requireMutable();
        if (unansweredQuestionCount <= 0 && requiredMissingFieldCount <= 0) {
            throw new IllegalStateException("NEEDS_INPUT requires an unanswered question or required missing field");
        }
        this.status = IdeaBriefStatus.NEEDS_INPUT;
        this.activeTaskRunId = null;
    }

    public void readyForReview() {
        requireMutable();
        this.status = IdeaBriefStatus.READY_FOR_REVIEW;
        this.activeTaskRunId = null;
    }

    public void applySafetyAndInterpretation(String decision, String categoriesJson,
            String restrictionsJson, String userFacingReason, String interpretationJson) {
        requireMutable();
        if (!("ALLOW".equals(decision) || "ALLOW_WITH_RESTRICTIONS".equals(decision)
                || "BLOCK_OR_REFRAME".equals(decision))
            || categoriesJson == null || restrictionsJson == null
            || userFacingReason == null || userFacingReason.isBlank() || userFacingReason.length() > 1_000
            || interpretationJson == null || interpretationJson.isBlank()) {
            throw new IllegalArgumentException("idea safety or interpretation is invalid");
        }
        this.safetyDecision = decision;
        this.safetyCategoriesJson = categoriesJson;
        this.safetyRestrictionsJson = restrictionsJson;
        this.safetyUserFacingReason = userFacingReason;
        this.interpretationJson = interpretationJson;
        this.interpretationConfirmedAt = null;
    }

    public void updateInterpretation(String interpretationJson) {
        requireMutable();
        if (interpretationJson == null || interpretationJson.isBlank()) {
            throw new IllegalArgumentException("idea interpretation is invalid");
        }
        this.interpretationJson = interpretationJson;
        this.interpretationConfirmedAt = null;
    }

    public void safetyBlocked() {
        requireMutable();
        if (!"BLOCK_OR_REFRAME".equals(safetyDecision)) {
            throw new IllegalStateException("safety decision does not block the pipeline");
        }
        this.status = IdeaBriefStatus.SAFETY_BLOCKED;
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
        this.interpretationConfirmedAt = LocalDateTime.now();
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

    public void copyCanonicalStateFrom(IdeaBrief source) {
        requireMutable();
        this.overviewText = source.overviewText;
        this.userFacingSummary = source.userFacingSummary;
        this.contradictionsJson = source.contradictionsJson;
        this.missingFieldKeysJson = source.missingFieldKeysJson;
        this.aiReadinessStatus = source.aiReadinessStatus;
        this.readinessScore = source.readinessScore;
        this.assessmentInputHash = source.assessmentInputHash;
        this.safetyDecision = source.safetyDecision;
        this.safetyCategoriesJson = source.safetyCategoriesJson;
        this.safetyRestrictionsJson = source.safetyRestrictionsJson;
        this.safetyUserFacingReason = source.safetyUserFacingReason;
        this.interpretationJson = source.interpretationJson;
        this.interpretationConfirmedAt = null;
    }

    public boolean isConfirmed() {
        return status == IdeaBriefStatus.CONFIRMED;
    }

    public void requireMutable() {
        if (isConfirmed()) throw new IllegalStateException("confirmed idea brief snapshot is immutable");
    }
}
