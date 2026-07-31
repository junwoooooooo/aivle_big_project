package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.analysis.legal.entity.ReviewMode;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.integration.ai.document.AiClientException;
import com.aivle.backend.integration.ai.legal.*;
import com.aivle.backend.job.runner.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LegalReviewJobExecutor implements AnalysisJobExecutor {
    private static final Logger log = LoggerFactory.getLogger(LegalReviewJobExecutor.class);

    private final LegalReviewJobContextService contexts;
    private final LegalReviewProgressService progress;
    private final LegalReviewAiClient aiClient;
    private final LegalReviewPersistenceService persistence;

    @Override public JobType jobType() { return JobType.LEGAL_REVIEW; }

    @Override
    public void execute(JobClaim claim) {
        var context = contexts.load(claim);
        progress.advance(claim, 20, "INPUT_SNAPSHOT_READY");
        if (context.mode() == ReviewMode.INCREMENTAL) {
            // 수용 기준: 승계 범주가 재실행되지 않음을 로그로 확인할 수 있어야 한다
            log.info("INCREMENTAL 재검토: rerunCategories={} carriedCategories={} changedSections={} "
                    + "— 승계 범주는 재실행하지 않는다",
                context.rerunCategories(), context.carriedCategories(), context.changedSections());
        }
        if (!context.confirmedFacts().isEmpty()) {
            log.info("확정 정보 {}건 주입: {}", context.confirmedFacts().size(),
                context.confirmedFacts().stream()
                    .map(LegalReviewAiRequest.ConfirmedFactPayload::key).toList());
        }
        try {
            var response = aiClient.review(new LegalReviewAiRequest(
                context.projectId(), context.planId(), context.sourceDocumentVersionId(),
                LegalReviewPolicy.PROMPT_VERSION, LegalReviewPolicy.PROMPT, context.sections(),
                context.mode(),
                context.mode() == ReviewMode.INCREMENTAL
                    ? List.copyOf(context.rerunCategories()) : List.of(),
                context.confirmedFacts()));
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
