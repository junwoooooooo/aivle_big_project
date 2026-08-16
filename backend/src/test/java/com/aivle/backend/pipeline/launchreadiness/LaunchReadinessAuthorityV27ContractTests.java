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
    void technologyAndOperationsBindConceptAndProfessionalDocumentTogether() throws Exception {
        String service = source("pipeline/launchreadiness/application/LaunchReadinessService.java");
        String entity = source("pipeline/launchreadiness/domain/LaunchReadinessInputSnapshot.java");
        assertThat(service).contains("CurrentConceptSourceResolver")
            .contains("launch-readiness-professional-input-v2")
            .contains("sourceBinding").contains("currentConcept").contains("professionalInput");
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
    void lateSuccessAndFailureCannotBecomeCurrentAfterConceptDrift() throws Exception {
        String service = source("pipeline/launchreadiness/application/LaunchReadinessService.java");
        assertThat(service).contains("snapshotHasher.hash(response.result()), exact, !exact")
            .contains("markStale(value, type, \"CONCEPT_CHANGED\")")
            .contains("status = stale ? \"STALE\"");
    }

    @Test
    void integratedManifestCarriesExactModuleResultInputAndConceptBinding() throws Exception {
        String bundle = source("pipeline/launchreadiness/application/LaunchReadinessReportBundleService.java");
        assertThat(bundle).contains("inputSnapshotId", "inputSnapshotHash", "resultHash", "sourceBinding")
            .contains("현재 사업안 기준으로 재무 분석이 필요합니다.");
    }

    @Test
    void financePinsCurrentConceptWithoutChangingDeterministicCalculator() throws Exception {
        String finance = source("pipeline/finance/application/FinancialService.java");
        String snapshot = source("pipeline/finance/application/FinancialInputSnapshotFactory.java");
        String calculator = source("pipeline/finance/application/FinancialCalculator.java");
        assertThat(finance).contains("CurrentConceptSourceResolver").contains("exactCurrentConcept")
            .contains("bind(snapshot, authority)");
        assertThat(snapshot).contains("sourceSelectionRevision", "sourceBmPlanRevision");
        assertThat(calculator).contains("public class FinancialCalculator");
    }

    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/aivle/backend", relative));
    }
}
