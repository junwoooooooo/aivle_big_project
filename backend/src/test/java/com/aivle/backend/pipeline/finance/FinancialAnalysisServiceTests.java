package com.aivle.backend.pipeline.finance;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.finance.service.FinancialSnapshotAnalysisService;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.finance.application.FinancialAnalysisService;
import com.aivle.backend.pipeline.finance.application.FinancialService;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FinancialAnalysisServiceTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final FinancialAnalysisService service = new FinancialAnalysisService(mock(FinancialService.class),
        mock(FinancialSnapshotAnalysisService.class), taskRuns, mock(TaskRunRepository.class),
        mock(TaskResultRepository.class), mock(CanonicalInputHasher.class), mock(JobEventPublisher.class), mapper);
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
