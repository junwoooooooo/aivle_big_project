package com.aivle.backend.job.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "app.jobs.document-processing",
    name = "enabled",
    havingValue = "true"
)
public class JobRunner {
    private final JobClaimService claimService;
    private final Map<com.aivle.backend.common.entity.JobType, AnalysisJobExecutor> executors;
    private final JobFailureService failureService;
    private final JobExecutionProperties properties;
    private final TaskExecutor taskExecutor;

    public JobRunner(
        JobClaimService claimService,
        List<AnalysisJobExecutor> executors,
        JobFailureService failureService,
        JobExecutionProperties properties,
        @Qualifier("documentJobExecutor") TaskExecutor taskExecutor
    ) {
        this.claimService = claimService;
        this.executors = executors.stream().collect(Collectors.toUnmodifiableMap(
            AnalysisJobExecutor::jobType, Function.identity()));
        this.failureService = failureService;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(fixedDelayString = "${app.jobs.document-processing.poll-interval}")
    public void poll() {
        List<RunningClaim> running = claimService.claimBatch().stream()
            .map(this::start)
            .toList();
        running.forEach(this::await);
    }

    public void wake(Long jobId) {
        Optional<JobClaim> claim = claimService.claimOne(jobId);
        claim.map(this::start).ifPresent(this::await);
    }

    private RunningClaim start(JobClaim claim) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            AnalysisJobExecutor executor = executors.get(claim.jobType());
            if (executor == null) {
                throw JobProcessingException.nonRetryable(
                    "JOB_TYPE_UNSUPPORTED", "지원하지 않는 작업 유형입니다.", null);
            }
            executor.execute(claim);
            return null;
        });
        taskExecutor.execute(task);
        return new RunningClaim(claim, task);
    }

    private void await(RunningClaim running) {
        JobClaim claim = running.claim();
        Future<Void> task = running.future();
        try {
            task.get(properties.executionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            if (
                claim.jobType()
                == com.aivle.backend.common.entity.JobType.SYSTEM_SMOKE_TEST
                || claim.jobType()
                == com.aivle.backend.common.entity.JobType.SYSTEM_ARTIFACT_SMOKE_TEST
                || claim.jobType()
                == com.aivle.backend.common.entity.JobType.MARKETING_GENERATION
            ) {
                failureService.handle(
                    claim,
                    JobProcessingException.nonRetryable(
                        "AI_TASK_TIMEOUT",
                        "AI 작업 시간이 초과되었습니다.",
                        exception
                    )
                );
                return;
            }
            failureService.handle(
                claim,
                JobProcessingException.retryable(
                    "DOCUMENT_PROCESSING_TIMEOUT",
                    "문서 처리 시간이 초과되어 다시 시도합니다.",
                    null,
                    exception
                )
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (
                claim.jobType()
                == com.aivle.backend.common.entity.JobType.SYSTEM_SMOKE_TEST
                || claim.jobType()
                == com.aivle.backend.common.entity.JobType.SYSTEM_ARTIFACT_SMOKE_TEST
                || claim.jobType()
                == com.aivle.backend.common.entity.JobType.MARKETING_GENERATION
            ) {
                failureService.handle(
                    claim,
                    JobProcessingException.nonRetryable(
                        "AI_TASK_INTERRUPTED",
                        "AI 작업이 중단되었습니다.",
                        exception
                    )
                );
                return;
            }
            failureService.handle(
                claim,
                JobProcessingException.retryable(
                    "DOCUMENT_PROCESSING_INTERRUPTED",
                    "문서 처리가 중단되어 다시 시도합니다.",
                    null,
                    exception
                )
            );
        } catch (ExecutionException exception) {
            failureService.handle(claim, classify(claim, exception.getCause()));
        }
    }

    private JobProcessingException classify(JobClaim claim, Throwable failure) {
        if (failure instanceof JobProcessingException processingFailure) {
            return processingFailure;
        }
        log.error("Unexpected analysis job failure, jobId={}, jobType={}, attempt={}",
            claim.jobId(), claim.jobType(), claim.attempt(), failure);
        if (
            claim.jobType()
            == com.aivle.backend.common.entity.JobType.SYSTEM_SMOKE_TEST
            || claim.jobType()
            == com.aivle.backend.common.entity.JobType.SYSTEM_ARTIFACT_SMOKE_TEST
            || claim.jobType()
            == com.aivle.backend.common.entity.JobType.MARKETING_GENERATION
        ) {
            return JobProcessingException.nonRetryable(
                "AI_TASK_EXECUTION_FAILED",
                "AI 작업 중 내부 오류가 발생했습니다.",
                failure
            );
        }
        return JobProcessingException.nonRetryable(
            "DOCUMENT_PROCESSING_FAILED",
            "문서 처리 중 내부 오류가 발생했습니다.",
            failure
        );
    }

    private record RunningClaim(JobClaim claim, Future<Void> future) {
    }
}
