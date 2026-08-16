package com.aivle.backend.pipeline.marketing.application;

import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Source;
import com.aivle.backend.pipeline.marketing.domain.*;
import com.aivle.backend.pipeline.marketing.repository.*;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.*;

@Service @RequiredArgsConstructor
public class MarketingContentCompletionService {
    private final MarketingContentRepository contents;
    private final MarketingSourceSnapshotRepository sources;
    private final CurrentConceptSourceResolver currentConcepts;
    private final MarketingContentRevisionRepository revisions;
    private final MarketingAssetRepository assets;
    private final MarketingResultContract contract;
    private final MarketingLegalGuard legalGuard;
    private final TaskRunService taskRuns;
    private final ObjectStoragePort objectStorage;
    private final ObjectMapper mapper;

    @Transactional
    public boolean start(String taskRunId, Long projectId) {
        MarketingContent content = lockedByTask(taskRunId, projectId);
        if (!bound(content)) {
            content.markStale();
            return false;
        }
        content.start();
        return true;
    }

    @Transactional
    public void complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        MarketingContent content = lockedByTask(claim.taskRunId(), context.projectId());
        contract.validate(response.result(), content.getContentType());
        legalGuard.validate(content.getSourceSnapshotJson(), response.result());
        String json = mapper.writeValueAsString(response.result());
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), json,
            response.canonicalInputHash(), response.resultSchemaVersion());
        int number = content.completeRevision();
        MarketingContentRevision revision = revisions.save(MarketingContentRevision.create(content.getId(), number,
            MarketingRevisionType.GENERATED, MarketingRevisionOrigin.AI, json, null));
        for (JsonNode ref : response.result().path("artifactRefs")) {
            validateArtifact(ref.asText());
            registerRollbackCleanup(ref.asText());
            assets.save(MarketingAsset.link(content.getId(), revision.getId(), ref.asText()));
        }
        if (!bound(content)) content.markStale();
    }

    @Transactional
    public void fail(TaskRunService.Claim claim, TaskRunWorkerContext context, String code, String reason, boolean retryable) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
        MarketingContent content = lockedByTask(claim.taskRunId(), context.projectId());
        if (bound(content)) content.fail(); else content.markStale();
    }

    private MarketingContent lockedByTask(String taskRunId, Long projectId) {
        MarketingContent value = contents.findByTaskRunIdAndDeletedAtIsNull(taskRunId)
            .orElseThrow(() -> new IllegalStateException("marketing content missing"));
        if (!projectId.equals(value.getProjectId())) throw new IllegalStateException("marketing project mismatch");
        return contents.findLocked(value.getId(), projectId)
            .orElseThrow(() -> new IllegalStateException("marketing content missing"));
    }

    private boolean bound(MarketingContent content) {
        Source current = currentConcepts.currentOrNull(content.getProjectId());
        MarketingSourceSnapshot source = sources.findById(content.getMarketingSourceSnapshotId()).orElse(null);
        return current != null && source != null
            && content.getSourceSnapshotHash().equals(source.getSnapshotHash())
            && source.getSourceSelectionRevision() != null
            && source.getSourceBmPlanRevision() != null
            && source.getSourceMarketSeedSnapshotId().equals(current.seed().getId())
            && source.getPortfolioSelectionId().equals(current.selection().getId())
            && source.getSourceSelectionRevision() == current.selection().getHypothesisRevision()
            && source.getSourceBmPlanRevision() == current.bm().revision();
    }

    private void validateArtifact(String artifactRef) {
        if (artifactRef == null || !artifactRef.matches(
                "ai-artifacts/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.jpg")
                || !objectStorage.exists(artifactRef)) {
            throw new IllegalArgumentException("marketing image artifact is missing");
        }
        try {
            ObjectStoragePort.ObjectMetadata metadata = objectStorage.metadata(artifactRef);
            if (!"image/jpeg".equals(metadata.contentType()) || metadata.sizeBytes() <= 0
                    || metadata.sizeBytes() > 20L * 1024 * 1024) {
                throw new IllegalArgumentException("marketing image artifact is invalid");
            }
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("marketing image artifact is unavailable", failure);
        }
    }

    private void registerRollbackCleanup(String artifactRef) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try { objectStorage.delete(artifactRef); }
                    catch (java.io.IOException | RuntimeException ignored) { /* reconciliation fallback */ }
                }
            }
        });
    }
}
