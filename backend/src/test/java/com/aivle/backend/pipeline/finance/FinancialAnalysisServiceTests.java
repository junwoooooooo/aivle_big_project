package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.finance.service.FinancialSnapshotAnalysisService;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import com.aivle.backend.pipeline.artifact.repository.ProjectEvidenceArtifactRepository;
import com.aivle.backend.pipeline.finance.api.FinancialApiModels.SnapshotView;
import com.aivle.backend.pipeline.finance.application.FinancialAnalysisService;
import com.aivle.backend.pipeline.finance.application.FinancialService;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FinancialAnalysisServiceTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final FinancialAnalysisService service = new FinancialAnalysisService(mock(FinancialService.class),
        mock(FinancialSnapshotAnalysisService.class), taskRuns, mock(TaskRunRepository.class),
        mock(TaskResultRepository.class), mock(CanonicalInputHasher.class), mock(JobEventPublisher.class),
        mock(ProjectEvidenceArtifactRepository.class), mapper);
    private final TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "token-1");
    private final String hash = "sha256:" + "a".repeat(64);

    @Test void aiReportChangesNarrativeOnlyAndKeepsDeterministicCalculation() {
        ExecutionResponse response = mock(ExecutionResponse.class);
        when(response.result()).thenReturn(mapper.readTree("""
            {"headline":"검증 결과","findings":["발견"],"cautions":["주의"],
             "recommendedActions":["행동"],"disclaimer":"추정치", "source":"AI_GENERATED_REPORT",
             "providerStatus":"SUCCEEDED","safeFailureReason":null}
            """));
        when(response.canonicalInputHash()).thenReturn(hash);
        service.complete(claim, context(), response);
        verify(taskRuns).adopt(eq("task-1"), eq("attempt-1"), eq("token-1"),
            argThat(json -> json.contains("\"calculation\":{\"marker\":7}")
                && json.contains("AI_GENERATED_REPORT")), eq(hash), eq("1.0"));
    }

    @Test void providerFailureAdoptsExplicitDeterministicFallback() {
        service.completeFallback(claim, context(), "AI_SERVICE_UNAVAILABLE");
        verify(taskRuns).adopt(eq("task-1"), eq("attempt-1"), eq("token-1"),
            argThat(json -> json.contains("SYSTEM_CALCULATION_FALLBACK")
                && json.contains("AI_SERVICE_UNAVAILABLE") && json.contains("\"marker\":7")),
            eq(hash), eq("1.0"));
    }

    @Test void currentRestoresSourceDocumentNameFromSnapshotArtifactLineage() {
        FinancialService finance = mock(FinancialService.class);
        TaskRunRepository runs = mock(TaskRunRepository.class);
        ProjectEvidenceArtifactRepository artifacts = mock(ProjectEvidenceArtifactRepository.class);
        ProjectEvidenceArtifact artifact = mock(ProjectEvidenceArtifact.class);
        var snapshotBody = mapper.createObjectNode()
            .put("sourceMode", "USER_DOCUMENT_INPUT")
            .put("sourceDocumentArtifactId", "artifact-1");
        var snapshot = new SnapshotView("FINANCIAL_INPUT_SNAPSHOT", "snapshot-1", "1.0", 41L,
            "preparation-1", null, null, null, null, hash, Instant.parse("2026-08-16T00:00:00Z"),
            snapshotBody, false);
        when(finance.currentSnapshot(7L, 41L)).thenReturn(snapshot);
        when(runs.findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, "FINANCIAL_ANALYSIS_REPORT", "USER_DOCUMENT_INPUT")).thenReturn(Optional.empty());
        when(artifacts.findByIdAndProjectIdAndDeletedAtIsNull("artifact-1", 41L))
            .thenReturn(Optional.of(artifact));
        when(artifact.getOriginalFilename()).thenReturn("my-finance-plan.docx");
        var currentService = new FinancialAnalysisService(finance,
            mock(FinancialSnapshotAnalysisService.class), mock(TaskRunService.class), runs,
            mock(TaskResultRepository.class), mock(CanonicalInputHasher.class),
            mock(JobEventPublisher.class), artifacts, mapper);

        assertThat(currentService.current(7L, 41L).sourceDocumentName())
            .isEqualTo("my-finance-plan.docx");
        when(artifacts.findByIdAndProjectIdAndDeletedAtIsNull("artifact-1", 41L))
            .thenReturn(Optional.empty());
        assertThat(currentService.current(7L, 41L).sourceDocumentName()).isNull();
        verify(artifacts, times(2)).findByIdAndProjectIdAndDeletedAtIsNull("artifact-1", 41L);
    }

    private TaskRunWorkerContext context() {
        String input = """
            {"snapshotId":"finance-1","deterministicResult":{"calculation":{"marker":7},
             "report":{"headline":"system","findings":["f"],"cautions":["c"],
             "recommendedActions":["a"],"disclaimer":"d","source":"SYSTEM_CALCULATION",
             "providerStatus":"NOT_REQUESTED","safeFailureReason":null}}}
            """;
        return new TaskRunWorkerContext("task-1", 41L, 7L, TaskType.FINANCE_ANALYSIS_REPORT,
            "FINANCIAL_ANALYSIS_REPORT", "finance-1", input, hash, "command-1", "request-1",
            "1.0", "1.0", "ko-KR", 1, 2);
    }
}
