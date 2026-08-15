package com.aivle.backend.pipeline.conceptportfolio.selection.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_portfolio_selections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptPortfolioSelection extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false, length = 64) private String runId;
    @Column(nullable = false, length = 64) private String conceptId;
    @Column(nullable = false, length = 200) private String candidateId;
    @Column(nullable = false, length = 71) private String selectedConceptHash;
    @Column(nullable = false, length = 71) private String baseLegalHash;
    @Column(nullable = false, length = 1000) private String selectionReason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50)
    private ConceptPortfolioSelectionStatus status;
    @Column(length = 64) private String activeTaskRunId;
    @Column(length = 40) private String activeAction;
    @Column(nullable = false) private int hypothesisRevision;
    @Column(nullable = false, length = 71) private String requestHash;
    @Column(nullable = false, length = 128) private String idempotencyKey;
    @Column(nullable = false) private Long selectedByUserId;
    @Column(nullable = false) private Instant selectedAt;
    @Column(nullable = false) private boolean isCurrent;
    @Column(length = 80) private String failureCode;

    public static ConceptPortfolioSelection create(Long projectId, String runId, String conceptId,
            String candidateId, String conceptHash, String legalHash, String reason,
            String requestHash, String idempotencyKey, Long userId, Instant now) {
        if (projectId == null || blank(runId) || blank(conceptId) || blank(candidateId)
                || !hash(conceptHash) || !hash(legalHash) || blank(reason) || reason.length() > 1000
                || !hash(requestHash) || blank(idempotencyKey) || idempotencyKey.length() > 128
                || userId == null || now == null) throw new IllegalArgumentException("Portfolio selection is invalid");
        ConceptPortfolioSelection value = new ConceptPortfolioSelection();
        value.projectId = projectId; value.runId = runId; value.conceptId = conceptId;
        value.candidateId = candidateId; value.selectedConceptHash = conceptHash;
        value.baseLegalHash = legalHash; value.selectionReason = reason;
        value.status = ConceptPortfolioSelectionStatus.HYPOTHESES_PREPARING;
        value.requestHash = requestHash; value.idempotencyKey = idempotencyKey;
        value.selectedByUserId = userId; value.selectedAt = now; value.isCurrent = true;
        return value;
    }

    public void attachTask(String taskRunId, String action) {
        if (!isCurrent || status == ConceptPortfolioSelectionStatus.STALE || blank(taskRunId)
                || blank(action) || activeTaskRunId != null) throw new IllegalStateException("Selection action cannot start");
        activeTaskRunId = taskRunId; activeAction = action;
        status = switch (action) {
            case "PREPARE_HYPOTHESES" -> ConceptPortfolioSelectionStatus.HYPOTHESES_PREPARING;
            case "DELTA_LEGAL" -> ConceptPortfolioSelectionStatus.DELTA_LEGAL_PENDING;
            case "BUILD_HANDOFF" -> ConceptPortfolioSelectionStatus.MARKET_SEED_FINALIZING;
            default -> status;
        };
    }

    public void completeTask(String taskRunId, ConceptPortfolioSelectionStatus next, boolean hypothesisChanged) {
        requireActive(taskRunId);
        activeTaskRunId = null; activeAction = null; failureCode = null; status = next;
        if (hypothesisChanged) hypothesisRevision++;
    }

    public void failTask(String taskRunId, ConceptPortfolioSelectionStatus next, String code) {
        requireActive(taskRunId); activeTaskRunId = null; activeAction = null;
        status = next; failureCode = code;
    }

    public void reportReady() {
        if (!isCurrent || status != ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT)
            throw new IllegalStateException("Legal report gate is closed");
        status = ConceptPortfolioSelectionStatus.LEGAL_REPORT_READY;
    }

    public void markStale() {
        isCurrent = false; status = ConceptPortfolioSelectionStatus.STALE;
        activeTaskRunId = null; activeAction = null;
    }

    public void reopenAfterHypothesisChange() {
        if (!isCurrent) throw new IllegalStateException("stale selection");
        status = ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION;
        hypothesisRevision++;
    }

    private void requireActive(String taskRunId) {
        if (!isCurrent || activeTaskRunId == null || !activeTaskRunId.equals(taskRunId))
            throw new IllegalStateException("Late selection action result");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
