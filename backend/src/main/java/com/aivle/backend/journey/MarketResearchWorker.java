package com.aivle.backend.journey;

import com.aivle.backend.taskrun.contract.MarketResearchContract;
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
     * 시장조사 전 구간은 <b>90~266초</b>라 2분 예산으로는 구조적으로 못 끝난다.
     * lease 는 예산보다 넉넉해야 한다 — 같거나 짧으면 정상 실행이 만료로 회수돼
     * 260초짜리가 중복 실행된다.
     */
    private static final Duration BUDGET = Duration.ofMinutes(6);
    private static final Duration LEASE = BUDGET.plusMinutes(2);

    private static final Set<String> FORBIDDEN_FIELDS = Set.of("storageUrl", "objectKey", "presignedUrl",
        "localPath", "fileBytes", "base64", "prompt", "rawProviderResponse", "credential");

    private final TaskRunService service;
    private final InternalAiExecutionClient client;
    private final ObjectMapper mapper;

    public MarketResearchWorker(TaskRunService service, InternalAiExecutionClient client, ObjectMapper mapper) {
        this.service = service;
        this.client = client;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${app.task-run.market-research-poll-interval-ms:2000}")
    public void poll() {
        processOne();
    }

    public boolean processOne() {
        TaskRunService.Claim claim = service.claimNext(TaskType.MARKET_RESEARCH, "market-research-worker", LEASE, BUDGET);
        if (claim == null) return false;
        service.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
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
                return true;
            }
            service.adopt(run.getId(), claim.taskAttemptId(), claim.claimToken(),
                mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
        } catch (ExecutionFailure failure) {
            if ("RESULT_SCHEMA_INVALID".equals(failure.code()))
                service.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "{}", "1.0", failure.reason());
            else service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                failure.code(), failure.reason(), failure.retryable());
        } catch (TaskRunFailure failure) {
            service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "RESULT_SCHEMA_INVALID", failure.getReason(), false);
        } catch (RuntimeException failure) {
            service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", true);
        }
        return true;
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
