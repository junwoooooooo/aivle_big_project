package com.aivle.backend.analysis.feasibility.application;

import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.integration.ai.document.AiClientException;
import com.aivle.backend.integration.ai.feasibility.FeasibilityAnalysisAiClient;
import com.aivle.backend.job.runner.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeasibilityJobExecutor implements AnalysisJobExecutor {
    private final FeasibilityJobContextService contexts;
    private final FeasibilityProgressService progress;
    private final FeasibilityAnalysisAiClient aiClient;
    private final FeasibilityPersistenceService persistence;

    @Override public JobType jobType() { return JobType.FEASIBILITY_ANALYSIS; }

    @Override
    public void execute(JobClaim claim) {
        var context = contexts.load(claim);
        progress.advance(claim, 20, "INPUT_SNAPSHOT_READY");
        try {
            var result = aiClient.analyze(context.request());
            progress.providerResponded(claim, result.providerRequestId());
            progress.advance(claim, 90, "RESULT_VALIDATED");
            persistence.complete(claim, context, result);
        } catch (AiClientException exception) {
            throw new JobProcessingException(
                exception.getErrorCode(), exception.getSafeMessage(),
                exception.isRetryable(), exception.getRetryAfter(), exception);
        } catch (IllegalArgumentException exception) {
            throw JobProcessingException.nonRetryable(
                "FEASIBILITY_AI_RESPONSE_INVALID",
                "사업 타당성 AI 결과 형식이 올바르지 않습니다.", exception);
        }
    }
}
