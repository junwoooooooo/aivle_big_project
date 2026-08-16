package com.aivle.backend.pipeline.marketing.worker;

import com.aivle.backend.jobevent.*;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.marketing.application.MarketingContentCompletionService;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.*;
import com.aivle.backend.taskrun.service.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class MarketingContentWorker {
    private static final TaskType TYPE = TaskType.MARKETING_CONTENT_GENERATION;
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final MarketingContentCompletionService completion;
    private final JobEventPublisher events;
    private final String workerId = "marketing-content-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${app.task-run.marketing-content-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.marketing-content-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE)))
            publish(taskRuns.workerContext(id), "QUEUED", "job.marketing.queued", JobEvent.Status.QUEUED, null);
    }

    public boolean processOne() {
        TaskRunService.Claim claim = taskRuns.claimNext(TYPE, workerId, Duration.ofMinutes(7), Duration.ofMinutes(5));
        if (claim == null) return false;
        TaskRunWorkerContext context = taskRuns.workerContext(claim.taskRunId());
        try {
            publish(context,"STARTED","job.marketing.started",JobEvent.Status.RUNNING,null);
            taskRuns.startExecution(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken());
            if (!completion.start(claim.taskRunId(),context.projectId())) {
                completion.fail(claim,context,"EXECUTION_FAILED","STALE_ACTION_RESULT",false);
                publish(context,"STALE","job.marketing.stale",JobEvent.Status.FAILED,"STALE_ACTION_RESULT");
                return true;
            }
            publish(context,"SOURCE_PREPARED","job.marketing.source_prepared",JobEvent.Status.RUNNING,null);
            publish(context,"COPY_GENERATING","job.marketing.copy_generating",JobEvent.Status.RUNNING,null);
            ExecutionResponse response=ai.executeWorker(context,claim.taskAttemptId(),LocalDateTime.now().plusMinutes(5));
            publish(context,"LEGAL_CHECKING","job.marketing.legal_checking",JobEvent.Status.RUNNING,null);
            completion.complete(claim,context,response);
            publish(context,"COMPLETED","job.marketing.completed",JobEvent.Status.COMPLETED,null);
        } catch (ExecutionFailure failure) {
            terminalFailure(claim,context,failure.code(),failure.reason(),failure.retryable());
        } catch (BusinessException failure) {
            if (failure.getErrorCode() == ErrorCode.MARKETING_PROHIBITED_CLAIM)
                terminalFailure(claim, context, "EXECUTION_FAILED", "SAFETY_POLICY_BLOCKED", false);
            else terminalFailure(claim, context, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
        } catch (RuntimeException failure) {
            log.warn("Marketing content worker failed taskRunId={} type={}",claim.taskRunId(),failure.getClass().getSimpleName());
            terminalFailure(claim,context,"RESULT_SCHEMA_INVALID","AI_RESULT_INVALID",false);
        }
        return true;
    }

    private void terminalFailure(TaskRunService.Claim claim,TaskRunWorkerContext context,String code,String reason,boolean retryable) {
        try { completion.fail(claim,context,code,reason,retryable); }
        finally { publish(context,"FAILED","job.marketing.failed",JobEvent.Status.FAILED,safeCode(code,reason)); }
    }
    private String safeCode(String code,String reason) {
        if ("SAFETY_POLICY_BLOCKED".equals(reason)) return "MARKETING_PROHIBITED_CLAIM";
        if ("REQUEST_DEADLINE_EXCEEDED".equals(reason)) return "TASK_TIMEOUT";
        if ("DEPENDENCY_RATE_LIMITED".equals(reason)) return "RATE_LIMITED";
        if ("AI_CONFIGURATION_INVALID".equals(reason)) return "AI_CONFIGURATION_INVALID";
        if ("RESULT_SCHEMA_INVALID".equals(code) || "INVALID_REQUEST".equals(code)) return "AI_RESULT_INVALID";
        return "AI_SERVICE_UNAVAILABLE";
    }
    private void publish(TaskRunWorkerContext c,String stage,String type,JobEvent.Status status,String code) {
        events.publish(new JobEventPublisher.Command(c.projectId(),c.taskRunId(),c.taskRunId(),stage,type,status,type,Map.of(),code));
    }
}
