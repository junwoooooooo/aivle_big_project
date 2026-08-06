package com.aivle.backend.persona.recommendation.application;

import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.integration.ai.document.AiClientException;
import com.aivle.backend.integration.ai.persona.PersonaRecommendationAiClient;
import com.aivle.backend.job.runner.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonaRecommendationJobExecutor implements AnalysisJobExecutor {
    private final PersonaJobContextService contexts;
    private final PersonaProgressService progress;
    private final PersonaRecommendationAiClient aiClient;
    private final PersonaRecommendationPersistenceService persistence;

    @Override public JobType jobType() { return JobType.PERSONA_RECOMMENDATION; }

    @Override
    public void execute(JobClaim claim) {
        var context = contexts.load(claim);
        progress.advance(claim, 20, "PERSONA_CATALOG_READY");
        progress.advance(claim, 50, "PERSONA_FIT_ANALYSIS");
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
                "PERSONA_AI_RESPONSE_INVALID",
                "Persona AI 결과 형식이 올바르지 않습니다.", exception);
        }
    }
}
