package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.integration.ai.document.AiClientException;
import com.aivle.backend.integration.ai.legal.*;
import com.aivle.backend.job.runner.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalReviewJobExecutor implements AnalysisJobExecutor {
    private final LegalReviewJobContextService contexts;
    private final LegalReviewProgressService progress;
    private final LegalReviewAiClient aiClient;
    private final LegalReviewPersistenceService persistence;

    @Override public JobType jobType() { return JobType.LEGAL_REVIEW; }

    @Override
    public void execute(JobClaim claim) {
        var context = contexts.load(claim);
        progress.advance(claim, 20, "INPUT_SNAPSHOT_READY");
        try {
            var response = aiClient.review(new LegalReviewAiRequest(
                context.projectId(), context.planId(), context.sourceDocumentVersionId(),
                LegalReviewPolicy.PROMPT_VERSION, LegalReviewPolicy.PROMPT, context.sections()));
            progress.providerResponded(claim, response.providerRequestId());
            persistence.complete(claim, context, response);
        } catch (AiClientException exception) {
            throw new JobProcessingException(
                exception.getErrorCode(), exception.getSafeMessage(),
                exception.isRetryable(), exception.getRetryAfter(), exception);
        } catch (IllegalArgumentException exception) {
            throw JobProcessingException.nonRetryable(
                "LEGAL_AI_RESPONSE_INVALID", "법률·규제 사전검토 결과 형식이 올바르지 않습니다.", exception);
        }
    }
}
