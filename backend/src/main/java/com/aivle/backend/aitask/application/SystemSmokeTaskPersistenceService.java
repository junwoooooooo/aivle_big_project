package com.aivle.backend.aitask.application;

import com.aivle.backend.aitask.entity.AiTaskResult;
import com.aivle.backend.aitask.repository.AiTaskResultRepository;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.integration.ai.AiServerException;
import com.aivle.backend.integration.ai.task.dto.AiTaskResponse;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SystemSmokeTaskPersistenceService {

    private static final String RESULT_REFERENCE_TYPE =
        "AI_TASK_RESULT";

    private final AnalysisJobRepository jobs;
    private final AiTaskResultRepository results;
    private final ObjectMapper objectMapper;
    private final Clock jobClock;

    @Transactional
    public void markDispatched(JobClaim claim) {
        var job = requireCurrent(claim);
        job.advance(
            claim.claimToken(),
            claim.attempt(),
            20,
            "AI_TASK_DISPATCHED",
            LocalDateTime.now(jobClock)
        );
    }

    @Transactional
    public Long complete(
        JobClaim claim,
        AiTaskResponse response
    ) {
        var job = requireCurrent(claim);
        if (
            response.execution() == null
            || response.result() == null
        ) {
            throw new IllegalArgumentException(
                "AI task result is incomplete"
            );
        }
        job.setExternalRequestId(
            claim.claimToken(),
            claim.attempt(),
            response.requestId()
        );
        AiTaskResult result = results.save(
            AiTaskResult.completed(
                job,
                response.schemaVersion(),
                response.requestId(),
                resultJson(response),
                response.execution().handler(),
                response.execution().handlerVersion()
            )
        );
        job.complete(
            claim.claimToken(),
            claim.attempt(),
            JobStatus.SUCCEEDED,
            RESULT_REFERENCE_TYPE,
            result.getId(),
            LocalDateTime.now(jobClock)
        );
        return result.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
        JobClaim claim,
        AiServerException failure
    ) {
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElse(null);
        if (
            job == null
            || !job.hasCurrentClaim(
                claim.claimToken(),
                claim.attempt()
            )
        ) {
            return;
        }
        if (
            failure.getRequestId() != null
            && !failure.getRequestId().isBlank()
        ) {
            job.setExternalRequestId(
                claim.claimToken(),
                claim.attempt(),
                failure.getRequestId()
            );
        }
        job.failAttempt(
            claim.claimToken(),
            claim.attempt(),
            failure.getErrorCode(),
            failure.getSafeMessage(),
            failure.isRetryable(),
            LocalDateTime.now(jobClock)
        );
    }

    private com.aivle.backend.job.entity.AnalysisJob requireCurrent(
        JobClaim claim
    ) {
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElseThrow(() ->
                new IllegalStateException("job does not exist")
            );
        if (
            !job.hasCurrentClaim(
                claim.claimToken(),
                claim.attempt()
            )
        ) {
            throw new IllegalStateException(
                "job claim is no longer current"
            );
        }
        return job;
    }

    private String resultJson(AiTaskResponse response) {
        try {
            return objectMapper.writeValueAsString(
                response.result()
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                "AI task result serialization failed",
                exception
            );
        }
    }
}
