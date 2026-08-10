package com.aivle.backend.journey;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.taskrun.contract.TwinSurveyContract;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 패널 트윈 조사 큐 폴러 겸 실행기.
 *
 * <p><b>이 빈이 없으면 TaskRun 이 영원히 QUEUED 로 남는다.</b> 공용 워커는 없다 —
 * 타입마다 자기 폴러를 만든다.
 *
 * <p>{@link MarketResearchWorker} 와 다른 점이 하나 있다: <b>{@link #recover()} 가 있다.</b>
 * n=300·4쌍이면 셀이 7,200개이고 그만큼 프로세스가 중간에 죽을 창이 넓다. lease 만료를
 * 회수하지 않으면 그 TaskRun 은 아무도 다시 집지 않아 화면이 영원히 돈다.
 */
@Component
public class TwinSurveyWorker {

    private static final Logger log = LoggerFactory.getLogger(TwinSurveyWorker.class);
    private static final TaskType TYPE = TaskType.TWIN_SURVEY;

    /**
     * 예산 12분. n=300·4쌍이면 1파만 7,200셀이고, 동시성 32 로도 분 단위다.
     * lease 는 예산보다 넉넉해야 한다 — 같거나 짧으면 정상 실행이 만료로 회수돼 중복 실행된다.
     */
    private static final Duration BUDGET = Duration.ofMinutes(12);
    private static final Duration LEASE = BUDGET.plusMinutes(3);

    private static final Set<String> FORBIDDEN_FIELDS = Set.of("storageUrl", "objectKey", "presignedUrl",
        "localPath", "fileBytes", "base64", "prompt", "rawProviderResponse", "credential");

    private final TaskRunService service;
    private final InternalAiExecutionClient client;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;
    private final String workerId = "twin-survey-" + UUID.randomUUID();

    public TwinSurveyWorker(TaskRunService service, InternalAiExecutionClient client,
                            JobEventPublisher events, ObjectMapper mapper) {
        this.service = service;
        this.client = client;
        this.events = events;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${app.task-run.twin-survey-poll-interval-ms:2000}")
    public void poll() {
        processOne();
    }

    /** lease 가 만료된 실행을 다시 큐에 올린다. 이것이 없으면 죽은 실행이 화면을 영원히 돌린다. */
    @Scheduled(fixedDelayString = "${app.task-run.twin-survey-recovery-interval-ms:15000}")
    public void recover() {
        for (String id : service.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            log.warn("Twin survey lease expired, requeued taskRunId={}", id);
            publish(service.workerContext(id), "QUEUED", "job.twin.survey.queued", JobEvent.Status.QUEUED, null);
        }
    }

    public boolean processOne() {
        TaskRunService.Claim claim = service.claimNext(TYPE, workerId, LEASE, BUDGET);
        if (claim == null) return false;
        TaskRunWorkerContext context = service.workerContext(claim.taskRunId());
        service.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("AI call must run outside a DB transaction");
        publish(context, "SURVEYING", "job.twin.survey.running", JobEvent.Status.RUNNING, null);
        try {
            TaskRun run = service.getOwnedForWorker(claim.taskRunId());
            ExecutionResponse response = client.execute(run, claim.taskAttemptId(), LocalDateTime.now().plus(BUDGET));
            try {
                // 이 검증이 없으면 결과가 조용히 폐기된다 — 컴파일도 테스트도 안 깨지고 AI 비용만 쓴다.
                TwinSurveyContract.validate(response.result());
                rejectForbiddenFields(response.result());
            } catch (ExecutionFailure invalidResult) {
                String safePayload = response.result() == null ? "{}" : mapper.writeValueAsString(response.result());
                service.rejectAndFail(run.getId(), claim.taskAttemptId(), claim.claimToken(), safePayload,
                    response.resultSchemaVersion() == null ? "1.0" : response.resultSchemaVersion(),
                    invalidResult.reason());
                publish(context, "FAILED", "job.twin.survey.failed", JobEvent.Status.FAILED, "AI_RESULT_INVALID");
                return true;
            }
            service.adopt(run.getId(), claim.taskAttemptId(), claim.claimToken(),
                mapper.writeValueAsString(response.result()), response.canonicalInputHash(),
                response.resultSchemaVersion());
            publish(context, "COMPLETED", "job.twin.survey.completed", JobEvent.Status.COMPLETED, null);
        } catch (ExecutionFailure failure) {
            if ("RESULT_SCHEMA_INVALID".equals(failure.code()))
                service.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "{}", "1.0",
                    failure.reason());
            else service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                failure.code(), failure.reason(), failure.retryable());
            publish(context, "FAILED", "job.twin.survey.failed", JobEvent.Status.FAILED, safeCode(failure));
        } catch (TaskRunFailure failure) {
            service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "RESULT_SCHEMA_INVALID", failure.getReason(), false);
            publish(context, "FAILED", "job.twin.survey.failed", JobEvent.Status.FAILED, "AI_RESULT_INVALID");
        } catch (RuntimeException failure) {
            log.warn("Twin survey worker failed taskRunId={} type={}",
                claim.taskRunId(), failure.getClass().getSimpleName());
            service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", true);
            publish(context, "FAILED", "job.twin.survey.failed", JobEvent.Status.FAILED, "AI_SERVICE_UNAVAILABLE");
        }
        return true;
    }

    /**
     * 실패 코드를 화면 문구로 옮긴다. <b>뱅크 미마운트는 따로 센다</b> —
     * 「AI 가 불안정하다」와 「자산이 안 붙었다」는 운영이 할 일이 다르다.
     */
    private String safeCode(ExecutionFailure failure) {
        if ("TWIN_BANK_UNAVAILABLE".equals(failure.reason())) return "TWIN_BANK_UNAVAILABLE";
        if ("TWIN_TASK_TYPE_NOT_SERVICEABLE".equals(failure.reason())) return "TWIN_TASK_TYPE_NOT_SERVICEABLE";
        if ("DEADLINE_EXCEEDED".equals(failure.code())) return "TASK_TIMEOUT";
        if ("RATE_LIMITED".equals(failure.code())) return "RATE_LIMITED";
        return "AI_SERVICE_UNAVAILABLE";
    }

    private void publish(TaskRunWorkerContext context, String stage, String key,
                         JobEvent.Status status, String code) {
        if (context == null) return;
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
