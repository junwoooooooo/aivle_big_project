package com.aivle.backend.pipeline.selection.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_selections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptSelection extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "concept_id", nullable = false, length = 64) private String conceptId;
    @Column(name = "selection_reason", nullable = false, length = 2000) private String selectionReason;
    @Column(name = "request_hash", nullable = false, length = 71) private String requestHash;
    @Column(name = "selected_by_user_id", nullable = false) private Long selectedByUserId;
    @Column(name = "selected_at", nullable = false) private Instant selectedAt;
    @Column(name = "is_current", nullable = false) private boolean currentSelection;
    @Column(name = "active_action_task_run_id", length = 64) private String activeActionTaskRunId;
    @Column(name = "pending_action_type", length = 40) private String pendingActionType;
    @Enumerated(EnumType.STRING) @Column(name = "pending_hypothesis_type", length = 40)
    private HypothesisType pendingHypothesisType;
    @Column(name = "pending_decision_id", length = 64) private String pendingDecisionId;
    @Column(name = "pending_proposal_version") private Integer pendingProposalVersion;
    @Column(name = "action_status", nullable = false, length = 40) private String actionStatus;
    @Column(name = "safe_action_error", length = 100) private String safeActionError;

    public static ConceptSelection select(Long projectId, String conceptId, String reason, String requestHash, Long userId, Instant selectedAt) {
        if (projectId == null || conceptId == null || conceptId.isBlank()) throw new IllegalArgumentException("project and concept are required");
        if (reason == null || reason.isBlank() || reason.length() > 2000) throw new IllegalArgumentException("selection reason is required");
        if (requestHash == null || !requestHash.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("request hash is invalid");
        ConceptSelection value = new ConceptSelection();
        value.projectId = projectId;
        value.conceptId = conceptId;
        value.selectionReason = reason.strip();
        value.requestHash = requestHash;
        value.selectedByUserId = userId;
        value.selectedAt = selectedAt;
        value.currentSelection = true;
        value.actionStatus = "IDLE";
        return value;
    }

    public void supersede() { currentSelection = false; }

    public void queueAction(String taskRunId, String actionType, HypothesisType hypothesisType,
            String decisionId, int proposalVersion) {
        if (taskRunId == null || actionType == null || hypothesisType == null || decisionId == null
                || proposalVersion < 1) throw new IllegalArgumentException("pending action fields are required");
        if (activeActionTaskRunId != null && !activeActionTaskRunId.equals(taskRunId)) {
            throw new IllegalStateException("another selection action is already active");
        }
        activeActionTaskRunId = taskRunId;
        pendingActionType = actionType;
        pendingHypothesisType = hypothesisType;
        pendingDecisionId = decisionId;
        pendingProposalVersion = proposalVersion;
        actionStatus = "QUEUED";
        safeActionError = null;
    }

    public void startAction(String taskRunId) {
        requirePending(taskRunId);
        actionStatus = "RUNNING";
    }

    public boolean pendingMatches(String taskRunId, HypothesisType type, String decisionId, int version) {
        return currentSelection && taskRunId != null && taskRunId.equals(activeActionTaskRunId)
            && type == pendingHypothesisType && decisionId.equals(pendingDecisionId)
            && pendingProposalVersion != null && pendingProposalVersion == version;
    }

    public void completeAction(String taskRunId, String outcome, String safeError) {
        requirePending(taskRunId);
        actionStatus = outcome;
        safeActionError = safeError;
        activeActionTaskRunId = null;
        pendingActionType = null;
        pendingHypothesisType = null;
        pendingDecisionId = null;
        pendingProposalVersion = null;
    }

    public boolean hasActiveAction() { return activeActionTaskRunId != null; }

    public void completeSynchronousAction() {
        if (hasActiveAction()) throw new IllegalStateException("selection action is active");
        actionStatus = "IDLE";
        safeActionError = null;
    }

    private void requirePending(String taskRunId) {
        if (taskRunId == null || !taskRunId.equals(activeActionTaskRunId)) {
            throw new IllegalStateException("selection action is stale");
        }
    }
}
