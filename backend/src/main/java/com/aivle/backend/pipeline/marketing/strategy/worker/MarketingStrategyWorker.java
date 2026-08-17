package com.aivle.backend.pipeline.marketing.strategy.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.pipeline.marketing.strategy.application.MarketingStrategyService;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketingStrategyWorker {

    private static final TaskType TYPE =
        TaskType.MARKETING_STRATEGY_GENERATION;

    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final MarketingStrategyService strategies;

    private final String workerId =
        "marketing-strategy-" + UUID.randomUUID();

    @Scheduled(
        fixedDelayString =
            "${app.task-run.marketing-strategy-poll-interval-ms:1000}"
    )
    public void poll() {
        processOne();
    }

    @Scheduled(
        fixedDelayString =
            "${app.task-run.marketing-strategy-recovery-interval-ms:5000}"
    )
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(
                Duration.ZERO,
                List.of(TYPE))) {
            var context = taskRuns.workerContext(id);

            strategies.publish(
                context.projectId(),
                context.taskRunId(),
                "QUEUED",
                "job.marketing.strategy.queued",
                JobEvent.Status.QUEUED,
                null
            );
        }
    }

    public boolean processOne() {
        var claim = taskRuns.claimNext(
            TYPE,
            workerId,
            Duration.ofMinutes(7),
            Duration.ofMinutes(5)
        );

        if (claim == null) {
            return false;
        }

        var context =
            taskRuns.workerContext(claim.taskRunId());

        try {
            taskRuns.startExecution(
                claim.taskRunId(),
                claim.taskAttemptId(),
                claim.claimToken()
            );

            strategies.publish(
                context.projectId(),
                context.taskRunId(),
                "ANALYZING",
                "job.marketing.strategy.analyzing",
                JobEvent.Status.RUNNING,
                null
            );

            var response = ai.executeWorker(
                context,
                claim.taskAttemptId(),
                LocalDateTime.now().plusMinutes(5)
            );

            strategies.complete(
                claim,
                context,
                response
            );

            strategies.publish(
                context.projectId(),
                context.taskRunId(),
                "COMPLETED",
                "job.marketing.strategy.completed",
                JobEvent.Status.COMPLETED,
                null
            );
        } catch (ExecutionFailure failure) {
            strategies.fail(
                claim,
                failure.code(),
                failure.reason(),
                failure.retryable()
            );

            strategies.publish(
                context.projectId(),
                context.taskRunId(),
                "FAILED",
                "job.marketing.strategy.failed",
                JobEvent.Status.FAILED,
                safeCode(failure)
            );
        } catch (RuntimeException failure) {
            log.warn(
                "Marketing strategy worker failed taskRunId={} type={}",
                claim.taskRunId(),
                failure.getClass().getSimpleName()
            );

            strategies.fail(
                claim,
                "RESULT_SCHEMA_INVALID",
                "AI_RESULT_INVALID",
                false
            );

            strategies.publish(
                context.projectId(),
                context.taskRunId(),
                "FAILED",
                "job.marketing.strategy.failed",
                JobEvent.Status.FAILED,
                "AI_RESULT_INVALID"
            );
        }

        return true;
    }

    private String safeCode(
        ExecutionFailure failure
    ) {
        if ("DEADLINE_EXCEEDED".equals(
                failure.code())) {
            return "TASK_TIMEOUT";
        }

        if ("RATE_LIMITED".equals(
                failure.code())) {
            return "RATE_LIMITED";
        }

        if ("RESULT_SCHEMA_INVALID".equals(
                failure.code())) {
            return "AI_RESULT_INVALID";
        }

        return "AI_SERVICE_UNAVAILABLE";
    }
}
