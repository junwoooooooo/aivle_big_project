package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable, source-bound proposal generation cursor. */
@Entity
@Table(name = "concept_refinement_rounds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptRefinementRound extends BaseEntity {
    public enum State {
        PROPOSING, AWAITING_DECISION, NO_CHANGES, FAILED, STALE,
        DECISION_RECORDED, KEEP_CURRENT, APPLYING_HYPOTHESES, APPLY_FAILED,
        LEGAL_REVIEW_PENDING, LEGAL_REVIEW_FAILED, LEGAL_BLOCKED,
        APPLIED_PENDING_FINALIZATION, FINALIZING, FINALIZATION_FAILED, FINALIZED,
        DECLINED, CONTINUED
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "selection_id", nullable = false) private Long selectionId;
    @Column(name = "business_validation_session_id", nullable = false, length = 64)
    private String businessValidationSessionId;
    @Column(name = "source_market_version_id", nullable = false) private Long sourceMarketVersionId;
    @Column(name = "source_bm_version_id", nullable = false) private Long sourceBmVersionId;
    @Column(name = "source_market_seed_snapshot_id", nullable = false, length = 64)
    private String sourceMarketSeedSnapshotId;
    @Column(name = "source_selection_revision", nullable = false) private Integer sourceSelectionRevision;
    @Column(name = "source_bm_plan_revision", nullable = false) private Integer sourceBmPlanRevision;
    @Column(name = "parent_round_id") private Long parentRoundId;
    @Column(name = "baseline_selection_revision") private Integer baselineSelectionRevision;
    @Column(name = "baseline_bm_plan_revision") private Integer baselineBmPlanRevision;
    @Column(name = "baseline_overlay_json", columnDefinition = "TEXT") private String baselineOverlayJson;
    @Column(name = "seed_rebuild_required", nullable = false) private boolean seedRebuildRequired;
    @Column(name = "round_number", nullable = false) private int roundNumber;
    @Column(name = "policy_version", nullable = false, length = 40) private String policyVersion;
    @Column(name = "task_run_id", nullable = false, length = 64) private String taskRunId;
    @Column(nullable = false) private int attempt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private State state;
    @Column(name = "command_idempotency_key", nullable = false, length = 128)
    private String commandIdempotencyKey;
    @Column(name = "canonical_material_hash", nullable = false, length = 71)
    private String canonicalMaterialHash;
    @Column(name = "proposal_json", columnDefinition = "TEXT") private String proposalJson;
    @Column(name = "drift_rejections_json", columnDefinition = "TEXT") private String driftRejectionsJson;
    @Column(name = "last_error_code", length = 80) private String lastErrorCode;
    @Column(name = "decision_json", columnDefinition = "TEXT") private String decisionJson;
    @Column(name = "decision_hash", length = 71) private String decisionHash;
    @Column(name = "decision_idempotency_key", length = 128) private String decisionIdempotencyKey;
    @Column(name = "decided_by_user_id") private Long decidedByUserId;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "application_idempotency_key", length = 128) private String applicationIdempotencyKey;
    @Column(name = "application_hash", length = 71) private String applicationHash;
    @Column(name = "application_task_run_id", length = 64) private String applicationTaskRunId;
    @Column(name = "delta_legal_task_run_id", length = 64) private String deltaLegalTaskRunId;
    @Column(name = "application_attempt") private Integer applicationAttempt;
    @Column(name = "applied_selection_revision") private Integer appliedSelectionRevision;
    @Column(name = "applied_bm_plan_revision") private Integer appliedBmPlanRevision;
    @Column(name = "application_error_code", length = 80) private String applicationErrorCode;
    @Column(name = "application_started_at") private Instant applicationStartedAt;
    @Column(name = "application_applied_at") private Instant applicationAppliedAt;
    @Column(name="finalization_idempotency_key",length=128) private String finalizationIdempotencyKey;
    @Column(name="finalization_hash",length=71) private String finalizationHash;
    @Column(name="finalization_task_run_id",length=64) private String finalizationTaskRunId;
    @Column(name="finalization_attempt") private Integer finalizationAttempt;
    @Column(name="finalization_error_code",length=80) private String finalizationErrorCode;
    @Column(name="final_market_seed_snapshot_id",length=64) private String finalMarketSeedSnapshotId;
    @Column(name="final_id") private Long finalId;
    @Column(name="finalized_at") private Instant finalizedAt;

    public static ConceptRefinementRound start(Long projectId, CompletedSource source,
            String taskRunId, String commandKey, String canonicalMaterialHash) {
        ConceptRefinementRound value = new ConceptRefinementRound();
        value.projectId = projectId;
        value.selectionId = source.selectionId();
        value.businessValidationSessionId = source.businessValidationSessionId();
        value.sourceMarketVersionId = source.marketVersionId();
        value.sourceBmVersionId = source.bmVersionId();
        value.sourceMarketSeedSnapshotId = source.marketSeedSnapshotId();
        value.sourceSelectionRevision = source.selectionRevision();
        value.sourceBmPlanRevision = source.bmPlanRevision();
        value.baselineSelectionRevision = source.selectionRevision();
        value.baselineBmPlanRevision = source.bmPlanRevision();
        value.baselineOverlayJson = "{}";
        value.seedRebuildRequired = false;
        value.roundNumber = 1;
        value.policyVersion = ConceptRefinementPolicy.VERSION;
        value.taskRunId = taskRunId;
        value.attempt = 1;
        value.state = State.PROPOSING;
        value.commandIdempotencyKey = commandKey;
        value.canonicalMaterialHash = canonicalMaterialHash;
        return value;
    }

    public static ConceptRefinementRound next(ConceptRefinementRound parent,
            int baselineSelectionRevision, int baselineBmPlanRevision,
            String baselineOverlayJson, boolean seedRebuildRequired,
            String taskRunId, String commandKey, String canonicalMaterialHash) {
        if (parent == null || parent.id == null || parent.roundNumber >= ConceptRefinementPolicy.MAX_ROUNDS)
            throw new IllegalStateException("Next refinement round is unavailable");
        ConceptRefinementRound value = new ConceptRefinementRound();
        value.projectId = parent.projectId;
        value.selectionId = parent.selectionId;
        value.businessValidationSessionId = parent.businessValidationSessionId;
        value.sourceMarketVersionId = parent.sourceMarketVersionId;
        value.sourceBmVersionId = parent.sourceBmVersionId;
        value.sourceMarketSeedSnapshotId = parent.sourceMarketSeedSnapshotId;
        value.sourceSelectionRevision = parent.sourceSelectionRevision;
        value.sourceBmPlanRevision = parent.sourceBmPlanRevision;
        value.parentRoundId = parent.id;
        value.baselineSelectionRevision = baselineSelectionRevision;
        value.baselineBmPlanRevision = baselineBmPlanRevision;
        value.baselineOverlayJson = baselineOverlayJson;
        value.seedRebuildRequired = seedRebuildRequired;
        value.roundNumber = parent.roundNumber + 1;
        value.policyVersion = ConceptRefinementPolicy.VERSION;
        value.taskRunId = taskRunId;
        value.attempt = 1;
        value.state = State.PROPOSING;
        value.commandIdempotencyKey = commandKey;
        value.canonicalMaterialHash = canonicalMaterialHash;
        return value;
    }

    public void declined() {
        if (state != State.AWAITING_DECISION) throw new IllegalStateException("Round cannot be declined");
        state = State.DECLINED;
    }

    public void continued() {
        if (state != State.APPLIED_PENDING_FINALIZATION)
            throw new IllegalStateException("Round cannot be continued");
        state = State.CONTINUED;
    }

    public int baselineSelectionRevision() {
        if (baselineSelectionRevision != null) return baselineSelectionRevision;
        if (roundNumber == 1 && sourceSelectionRevision != null) return sourceSelectionRevision;
        throw new IllegalStateException("Refinement baseline selection revision is unavailable");
    }

    public int baselineBmPlanRevision() {
        if (baselineBmPlanRevision != null) return baselineBmPlanRevision;
        if (roundNumber == 1 && sourceBmPlanRevision != null) return sourceBmPlanRevision;
        throw new IllegalStateException("Refinement baseline BM revision is unavailable");
    }

    public String baselineOverlayJson() {
        if (baselineOverlayJson != null) return baselineOverlayJson;
        if (roundNumber == 1) return "{}";
        throw new IllegalStateException("Refinement baseline overlay is unavailable");
    }

    public void retry(String taskRunId, String commandKey, String canonicalMaterialHash) {
        if (state != State.FAILED || attempt >= ConceptRefinementPolicy.MAX_ATTEMPTS_PER_ROUND) {
            throw new IllegalStateException("Refinement retry is unavailable");
        }
        this.taskRunId = taskRunId;
        this.commandIdempotencyKey = commandKey;
        this.canonicalMaterialHash = canonicalMaterialHash;
        this.attempt += 1;
        this.state = State.PROPOSING;
        this.lastErrorCode = null;
    }

    public void materialize(String proposalJson, String driftRejectionsJson, boolean hasProposals) {
        if (state != State.PROPOSING) throw new IllegalStateException("Refinement is not proposing");
        this.proposalJson = proposalJson;
        this.driftRejectionsJson = driftRejectionsJson;
        this.lastErrorCode = null;
        this.state = hasProposals ? State.AWAITING_DECISION : State.NO_CHANGES;
    }

    public void fail(String safeErrorCode) {
        if (state == State.PROPOSING) {
            state = State.FAILED;
            lastErrorCode = safeErrorCode;
        }
    }

    public void markStale() { state = State.STALE; }

    public void recordDecision(String decisionJson, String decisionHash,
            String idempotencyKey, Long userId, Instant now, boolean keepCurrent) {
        if (state != State.AWAITING_DECISION || this.decisionJson != null) {
            throw new IllegalStateException("Refinement decision is unavailable");
        }
        if (decisionJson == null || decisionJson.isBlank()
                || decisionHash == null || !decisionHash.matches("sha256:[0-9a-f]{64}")
                || idempotencyKey == null || idempotencyKey.isBlank()
                || userId == null || now == null) {
            throw new IllegalArgumentException("Refinement decision is invalid");
        }
        this.decisionJson = decisionJson;
        this.decisionHash = decisionHash;
        this.decisionIdempotencyKey = idempotencyKey;
        this.decidedByUserId = userId;
        this.decidedAt = now;
        this.state = keepCurrent ? State.KEEP_CURRENT : State.DECISION_RECORDED;
    }

    public void startApplication(String key, String hash, String taskRunId, Instant now) {
        requireApplicationStart(State.DECISION_RECORDED, key, hash, now);
        if (taskRunId == null || taskRunId.isBlank()) throw new IllegalArgumentException("application task is required");
        applicationTaskRunId = taskRunId;
        applicationAttempt = 1;
        state = State.APPLYING_HYPOTHESES;
    }

    public void startLocalApplication(String key, String hash, Instant now) {
        requireApplicationStart(State.DECISION_RECORDED, key, hash, now);
        applicationAttempt = 1;
    }

    public void retryApplication(String key, String hash, String taskRunId, Instant now) {
        if (state != State.APPLY_FAILED || applicationAttempt == null || applicationAttempt >= 3)
            throw new IllegalStateException("application retry is unavailable");
        requireApplicationIdentity(key, hash, now);
        if (taskRunId == null || taskRunId.isBlank()) throw new IllegalArgumentException("application task is required");
        applicationTaskRunId = taskRunId;
        applicationAttempt += 1;
        applicationErrorCode = null;
        state = State.APPLYING_HYPOTHESES;
    }

    public void applicationFailed(String taskRunId, String errorCode) {
        if (state != State.APPLYING_HYPOTHESES || !java.util.Objects.equals(applicationTaskRunId, taskRunId))
            throw new IllegalStateException("application failure is stale");
        applicationErrorCode = errorCode;
        state = State.APPLY_FAILED;
    }

    public void recordAppliedLineage(int selectionRevision, int bmPlanRevision, Instant now) {
        if (state != State.APPLYING_HYPOTHESES && state != State.DECISION_RECORDED)
            throw new IllegalStateException("application lineage is unavailable");
        appliedSelectionRevision = selectionRevision;
        appliedBmPlanRevision = bmPlanRevision;
        applicationAppliedAt = now;
        applicationErrorCode = null;
    }

    public void legalPending(String taskRunId) {
        if (appliedSelectionRevision == null || appliedBmPlanRevision == null
                || taskRunId == null || taskRunId.isBlank())
            throw new IllegalStateException("legal review cannot start");
        deltaLegalTaskRunId = taskRunId;
        state = State.LEGAL_REVIEW_PENDING;
    }

    public void retryLegal(String taskRunId) {
        if (state != State.LEGAL_REVIEW_FAILED || taskRunId == null || taskRunId.isBlank())
            throw new IllegalStateException("legal retry is unavailable");
        deltaLegalTaskRunId = taskRunId;
        applicationErrorCode = null;
        state = State.LEGAL_REVIEW_PENDING;
    }

    public void legalFailed(String taskRunId, String errorCode) {
        requireDeltaTask(taskRunId);
        applicationErrorCode = errorCode;
        state = State.LEGAL_REVIEW_FAILED;
    }

    public void legalBlocked(String taskRunId) {
        requireDeltaTask(taskRunId);
        applicationErrorCode = null;
        state = State.LEGAL_BLOCKED;
    }

    public void readyForFinalization() {
        if (appliedSelectionRevision == null || appliedBmPlanRevision == null
                || !java.util.Set.of(State.DECISION_RECORDED, State.APPLYING_HYPOTHESES,
                    State.LEGAL_REVIEW_PENDING).contains(state))
            throw new IllegalStateException("refinement is not applied");
        applicationErrorCode = null;
        state = State.APPLIED_PENDING_FINALIZATION;
    }

    public boolean postApplyState() {
        return java.util.Set.of(State.LEGAL_REVIEW_PENDING, State.LEGAL_REVIEW_FAILED,
            State.LEGAL_BLOCKED, State.APPLIED_PENDING_FINALIZATION, State.FINALIZING,
            State.FINALIZATION_FAILED, State.FINALIZED).contains(state);
    }

    public void recordResolvedLineage(int selectionRevision, int bmRevision) {
        if (!java.util.Set.of(State.KEEP_CURRENT, State.NO_CHANGES).contains(state))
            throw new IllegalStateException("resolved lineage unavailable");
        appliedSelectionRevision=selectionRevision; appliedBmPlanRevision=bmRevision;
    }
    public void startFinalization(String key,String hash,String taskId,Instant now) {
        if (!java.util.Set.of(State.APPLIED_PENDING_FINALIZATION,State.KEEP_CURRENT,State.NO_CHANGES).contains(state))
            throw new IllegalStateException("finalization unavailable");
        requireFinalization(key,hash,now); finalizationAttempt=1; finalizationTaskRunId=taskId;
        state=taskId==null?state:State.FINALIZING;
    }
    public void retryFinalization(String key,String hash,String taskId,Instant now) {
        if(state!=State.FINALIZATION_FAILED||finalizationAttempt==null||finalizationAttempt>=3)
            throw new IllegalStateException("finalization retry unavailable");
        requireFinalization(key,hash,now); finalizationAttempt++; finalizationTaskRunId=taskId;
        finalizationErrorCode=null; state=State.FINALIZING;
    }
    public void finalizationFailed(String taskId,String code) {
        if(state!=State.FINALIZING||!java.util.Objects.equals(finalizationTaskRunId,taskId))
            throw new IllegalStateException("stale finalization failure");
        finalizationErrorCode=code; state=State.FINALIZATION_FAILED;
    }
    public void finalized(Long finalId,String seedId,Instant now) {
        if(!java.util.Set.of(State.APPLIED_PENDING_FINALIZATION,State.KEEP_CURRENT,State.NO_CHANGES,State.FINALIZING).contains(state)
                ||finalId==null||seedId==null||now==null) throw new IllegalStateException("finalization unavailable");
        this.finalId=finalId; this.finalMarketSeedSnapshotId=seedId; finalizedAt=now;
        finalizationErrorCode=null; state=State.FINALIZED;
    }
    private void requireFinalization(String key,String hash,Instant now){
        if(key==null||key.isBlank()||hash==null||!hash.matches("sha256:[0-9a-f]{64}")||now==null)
            throw new IllegalArgumentException("finalization identity invalid");
        finalizationIdempotencyKey=key; finalizationHash=hash;
    }

    private void requireApplicationStart(State expected, String key, String hash, Instant now) {
        if (state != expected) throw new IllegalStateException("application is unavailable");
        requireApplicationIdentity(key, hash, now);
    }

    private void requireApplicationIdentity(String key, String hash, Instant now) {
        if (key == null || key.isBlank() || hash == null || !hash.matches("sha256:[0-9a-f]{64}") || now == null)
            throw new IllegalArgumentException("application identity is invalid");
        applicationIdempotencyKey = key;
        applicationHash = hash;
        applicationStartedAt = now;
    }

    private void requireDeltaTask(String taskRunId) {
        if (state != State.LEGAL_REVIEW_PENDING || !java.util.Objects.equals(deltaLegalTaskRunId, taskRunId))
            throw new IllegalStateException("delta legal result is stale");
    }

    public boolean boundTo(CompletedSource source) {
        return source != null
            && java.util.Objects.equals(businessValidationSessionId, source.businessValidationSessionId())
            && java.util.Objects.equals(sourceMarketVersionId, source.marketVersionId())
            && java.util.Objects.equals(sourceBmVersionId, source.bmVersionId())
            && java.util.Objects.equals(sourceMarketSeedSnapshotId, source.marketSeedSnapshotId())
            && java.util.Objects.equals(selectionId, source.selectionId())
            && java.util.Objects.equals(sourceSelectionRevision, source.selectionRevision())
            && java.util.Objects.equals(sourceBmPlanRevision, source.bmPlanRevision());
    }
}
