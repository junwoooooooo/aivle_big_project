package com.aivle.backend.pipeline.market;

import com.aivle.backend.taskrun.contract.MarketResearchContract;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 시장조사·BM 큐 폴러 겸 실행기.
 *
 * <p><b>이 빈이 없으면 TaskRun 이 영원히 QUEUED 로 남는다.</b> 타입 지정 폴러만 있고,
 * 새 TaskType 은 자기 것을 만들어야 한다.
 *
 * <p>주기가 다른 워커(1초)보다 긴 이유: 한 번 잡히면 <b>90~266초</b> 도는 작업이라
 * 짧게 폴링해 봐야 빈 조회만 늘어난다.
 */
@Component
public class MarketResearchWorker {
    /**
     * 저장 원장 재채점은 90~266초지만 새 Product concept은 harness·dryrun·fresh collection까지
     * 수행한다. Main의 20분 실행 의도를 보존해야 이미 지불한 수집 결과가 짧은 deadline 때문에
     * 폐기되지 않는다.
     * lease 는 예산보다 넉넉해야 한다 — 같거나 짧으면 정상 실행이 만료로 회수돼
     * 260초짜리가 중복 실행된다.
     */
    static final Duration BUDGET = Duration.ofMinutes(20);
    static final Duration LEASE = BUDGET.plusMinutes(2);

    private static final Set<String> FORBIDDEN_FIELDS = Set.of("storageUrl", "objectKey", "presignedUrl",
        "localPath", "fileBytes", "base64", "prompt", "rawProviderResponse", "credential");

    private final TaskRunService service;
    private final MarketResearchService completion;
    private final JobEventPublisher events;
    private final InternalAiExecutionClient client;
    private final ObjectMapper mapper;

    public MarketResearchWorker(TaskRunService service, InternalAiExecutionClient client,
                                MarketResearchService completion, JobEventPublisher events,
                                ObjectMapper mapper) {
        this.service = service;
        this.client = client;
        this.completion = completion;
        this.events = events;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${app.task-run.market-research-poll-interval-ms:2000}")
    public void poll() {
        processOne();
    }

    public boolean processOne() {
        TaskRunService.Claim claim = service.claimNext(TaskType.MARKET_RESEARCH, "market-research-worker", LEASE, BUDGET);
        if (claim == null) return false;
        var context = service.workerContext(claim.taskRunId());
        service.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        completion.markRunning(claim.taskRunId());
        publish(context, "PREPARING_INPUT", key(context, "preparing"), JobEvent.Status.RUNNING, null);
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("AI call must run outside a DB transaction");
        try {
            TaskRun run = service.getOwnedForWorker(claim.taskRunId());
            ExecutionResponse response = client.execute(run, claim.taskAttemptId(), LocalDateTime.now().plus(BUDGET));
            try {
                // 이 검증이 없으면 결과가 조용히 폐기된다 — 컴파일도 테스트도 안 깨지고 AI 비용만 쓴다.
                MarketResearchContract.validate(response.result());
                rejectForbiddenFields(response.result());
            } catch (ExecutionFailure invalidResult) {
                String safePayload = response.result() == null ? "{}" : mapper.writeValueAsString(response.result());
                service.rejectAndFail(run.getId(), claim.taskAttemptId(), claim.claimToken(), safePayload,
                    response.resultSchemaVersion() == null ? "1.0" : response.resultSchemaVersion(),
                    invalidResult.reason());
                completion.materializeFailure(run.getId(), "RESULT_SCHEMA_INVALID");
                publish(context, "FAILED", key(context, "failed"),
                    JobEvent.Status.FAILED, "AI_RESULT_INVALID");
                return true;
            }
            completion.complete(claim, response);
            publish(context, "COMPLETED", key(context, "completed"), JobEvent.Status.COMPLETED, null);
        } catch (ExecutionFailure failure) {
            if ("RESULT_SCHEMA_INVALID".equals(failure.code()))
                service.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "{}", "1.0", failure.reason());
            else service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                failure.code(), failure.reason(), failure.retryable());
            completion.materializeFailure(claim.taskRunId(), failure.code());
            publish(context, "FAILED", key(context, "failed"), JobEvent.Status.FAILED, failure.code());
        } catch (TaskRunFailure failure) {
            service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "RESULT_SCHEMA_INVALID", failure.getReason(), false);
            completion.materializeFailure(claim.taskRunId(), "RESULT_SCHEMA_INVALID");
            publish(context, "FAILED", key(context, "failed"), JobEvent.Status.FAILED, "AI_RESULT_INVALID");
        } catch (RuntimeException failure) {
            service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", true);
            completion.materializeFailure(claim.taskRunId(), "EXECUTION_FAILED");
            publish(context, "FAILED", key(context, "failed"), JobEvent.Status.FAILED, "AI_SERVICE_UNAVAILABLE");
        }
        return true;
    }

    private String key(com.aivle.backend.taskrun.service.TaskRunWorkerContext context, String suffix) {
        return ("MARKET_RESEARCH_BM".equals(context.subjectType())
            ? "job.business-model." : "job.market.research.") + suffix;
    }

    private void publish(com.aivle.backend.taskrun.service.TaskRunWorkerContext context,
                         String stage, String key, JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(),
            context.taskRunId(), stage, key, status, key, Map.of(), code));
    }

    private void rejectForbiddenFields(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                if (FORBIDDEN_FIELDS.contains(name))
                    throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_UNKNOWN_FIELD", false);
                rejectForbiddenFields(node.get(name));
            }
        } else if (node.isArray()) {
            node.forEach(this::rejectForbiddenFields);
        }
    }
}
