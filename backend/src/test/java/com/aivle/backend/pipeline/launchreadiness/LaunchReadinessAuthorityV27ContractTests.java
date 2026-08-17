package com.aivle.backend.pipeline.launchreadiness;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Static contract guards for the deferred V27 integration gate. */
class LaunchReadinessAuthorityV27ContractTests {
    @Test
    void marketingStaleCommandsPersistWithoutThrowingAndWorkerSkipsProvider() throws Exception {
        String service = source("pipeline/marketing/application/MarketingContentService.java");
        String completion = source("pipeline/marketing/application/MarketingContentCompletionService.java");
        String worker = source("pipeline/marketing/worker/MarketingContentWorker.java");
        assertThat(service).contains("previous.markStale();").contains("return view(previous, authority);")
            .contains("if (markIfStale(content, authority)) return view(content, authority);");
        assertThat(completion).contains("public boolean start").contains("content.markStale();").contains("return false;");
        assertThat(worker).contains("if (!completion.start").contains("STALE_ACTION_RESULT");
    }

    @Test
    void technologyAndOperationsUseOnlyOwnedProjectAndProfessionalDocument() throws Exception {
        String service = source("pipeline/launchreadiness/application/LaunchReadinessService.java");
        String entity = source("pipeline/launchreadiness/domain/LaunchReadinessInputSnapshot.java");
        assertThat(service).contains("launch-readiness-professional-input-v2")
            .contains("professionalInput").contains("requireOwned(ownerId, projectId)");
        assertThat(service).doesNotContain("LaunchReadinessConceptSourceResolver", "requireAuthority",
            "sourceBinding", "currentConcept", "selectedConceptHash", "selectionRevision",
            "marketSeedSnapshotId", "bmPlanRevision", "conceptContext", "first(");
        assertThat(entity).contains("source_market_seed_snapshot_id", "source_selection_id",
            "source_selection_revision", "source_bm_plan_revision", "source_binding_hash");
    }

    @Test
    void moduleDocumentSupersessionIsScopedToOneModule() throws Exception {
        String service = source("pipeline/launchreadiness/application/LaunchReadinessService.java");
        assertThat(service).contains("findFirstByProjectIdAndModuleTypeAndCurrentTrue")
            .doesNotContain("supersedeAllModules");
    }

    @Test
    void idempotencyComparesCanonicalDocumentAndSourceBeforeArtifactStorage() throws Exception {
        String service = source("pipeline/launchreadiness/application/LaunchReadinessService.java");
        assertThat(service.indexOf("artifacts.fingerprint(file)"))
            .isLessThan(service.indexOf("artifacts.upload(ownerId, projectId, file)"));
        assertThat(service).contains("IDEMPOTENCY_CONFLICT")
            .contains("snapshotHash.equals(replayInput.path(\"inputSnapshotHash\")");
    }

    @Test
    void failedRetryIsManualSourceExactAndBoundedToThreeAttempts() throws Exception {
        String service = source("pipeline/launchreadiness/application/LaunchReadinessService.java");
        assertThat(service).contains("public AnalysisActionResponse retry")
            .contains("latest.getState() != TaskRunState.FAILED")
            .contains("previous.getAttempt() >= 3")
            .contains("previous.getAttempt() + 1");
        assertThat(service).contains("correlationId == null || correlationId.isBlank() ? key : correlationId, 1");
    }

    @Test
    void lateSuccessCannotBecomeCurrentAfterDocumentSupersession() throws Exception {
        String service = source("pipeline/launchreadiness/application/LaunchReadinessService.java");
        assertThat(service).contains("snapshotHasher.hash(response.result()), exact, !exact")
            .contains("markStale(source, type, \"DOCUMENT_SUPERSEDED\")")
            .contains("status = stale ? \"STALE\"");
    }

    @Test
    void integratedManifestCarriesExactModuleResultInputWithoutLaunchConceptBinding() throws Exception {
        String bundle = source("pipeline/launchreadiness/application/LaunchReadinessReportBundleService.java");
        assertThat(bundle).contains("inputSnapshotId", "inputSnapshotHash", "resultHash")
            .contains("현재 전문 입력 기준으로");
    }

    @Test
    void financeSupportsIndependentInputWhileRetainingOptionalContextAndDeterministicCalculator() throws Exception {
        String finance = source("pipeline/finance/application/FinancialService.java");
        String snapshot = source("pipeline/finance/application/FinancialInputSnapshotFactory.java");
        String calculator = source("pipeline/finance/application/FinancialCalculator.java");
        assertThat(finance).contains("CurrentConceptSourceResolver", "exactCurrentConcept", "DIRECT_INPUT",
            "USER_DOCUMENT_INPUT", "createIndependent");
        assertThat(snapshot).contains("sourceSelectionRevision", "sourceBmPlanRevision", "createIndependent");
        assertThat(calculator).contains("public class FinancialCalculator");
    }

    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/aivle/backend", relative));
    }
}
