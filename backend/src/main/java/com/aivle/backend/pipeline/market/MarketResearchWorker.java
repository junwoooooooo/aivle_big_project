package com.aivle.backend.pipeline.market;

import com.aivle.backend.taskrun.contract.MarketResearchContract;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.pipeline.refinement.ConceptRefinementService;
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
     * 수행한다. 이미 지불한 수집 결과가 짧은 deadline 때문에 폐기되지 않아야 한다.
     *
     * <p>lease 는 예산보다 넉넉해야 한다 — 같거나 짧으면 정상 실행이 만료로 회수돼
     * 중복 실행된다.
     *
     * <p>★ <b>20 → 60분 (2026-08-16 병합).</b> 20분은 <b>옛 엔진</b>의 수였다. 이 판에서
     * 시장조사 엔진이 두 가지로 커졌다:
     *
     * <ul>
     *   <li>재질문이 문서의 46%에만 닿던 것을 전량으로 — 호출 수 <b>266 → 470</b>(≈1.8배)</li>
     *   <li>발췌가 <b>추론 모델</b>이 됐다 — 호출당 시간도 는다</li>
     * </ul>
     *
     * 그래서 {@code MarketResearchInputFactory.LLM_BUDGET_FULL} 도 90 → 500 이다. 20분을
     * 그대로 두면 <b>절 체인 도중에 잘린다.</b>
     *
     * <p>⚠ <b>넘기면 그냥 실패가 아니다.</b> {@code REQUEST_DEADLINE_EXCEEDED} 는 retryable 이라
     * <b>같은 것을 한 번 더 태운다</b> — 이미 지불한 수집 비용을 잃고 그만큼을 또 쓴다.
     * 그래서 <b>넉넉한 쪽으로 틀린다.</b> 예산은 상한이지 지출이 아니라, 빨리 끝나면 빨리 끝난다.
     *
     * <p>⚠ 60분은 <b>산수지 실측이 아니다</b>(23분 × 1.8 ≈ 41분 + 추론 모델 여유).
     * 첫 유료 재실행에서 <b>실제 벽시계를 재고 이 숫자를 고친다.</b>
     */
    static final Duration BUDGET = Duration.ofMinutes(60);
    static final Duration LEASE = BUDGET.plusMinutes(3);

    private static final Set<String> FORBIDDEN_FIELDS = Set.of("storageUrl", "objectKey", "presignedUrl",
        "localPath", "fileBytes", "base64", "prompt", "rawProviderResponse", "credential");

    /**
     * 다듬기 첫 라운드를 <b>이 subjectType 일 때만</b> 건다.
     *
     * <p>{@code MarketResearchService.start} 가 {@code "MARKET_RESEARCH_" + kind} 로 적는다 —
     * FULL 과 BM 이 <b>같은 워커</b>를 쓰므로 이 한 줄이 둘을 가르는 유일한 자리다.
     *
     * <p>★ <b>왜 FULL 이 아니라 BM 인가.</b> 다듬기의 재료는 캔버스와 게이트 사유인데
     * ({@code ConceptRefinementService} 가 읽는 {@code canvas}·{@code gateReasons}),
     * {@code MarketResearchContract} 가 <b>FULL 모드에서 그 둘을 null 로 강제</b>한다.
     * 게다가 재료를 고르는 {@code latestValidationVersion} 은 {@code VALIDATION}·{@code BM}
     * 만 보고 <b>FULL 판은 쳐다보지도 않는다.</b> FULL 에 걸면 라운드 1이 근거 없이
     * 조용히 돌거나(400 도 안 난다 — AI 계약상 전부 optional) 직전 판을 재료로 쓴다.
     *
     * <p>제품 순서와도 이쪽이 맞는다: 시장조사 → BM → 다듬기. 화면에서도 다듬기 구획은
     * 캔버스 <b>아래</b>에 선다({@code BmCanvasPage}).
     */
    static final String REFINEMENT_TRIGGER_SUBJECT = "MARKET_RESEARCH_BM";

    /**
     * BM 을 <b>이 subjectType 일 때만</b> 이어 건다 — 즉 시장조사(FULL) 가 끝난 직후.
     *
     * <p>★ <b>버튼 하나로 세 걸음</b>(2026-08-16 사용자 결정). 사업 검증은
     * 시장조사 → BM → 컨셉 다듬기 세 걸음인데, 예전에는 걸음마다 사람이 버튼을 눌러야
     * 이어졌다. 재료는 앞 걸음이 다 만들어 주므로 <b>사람이 고를 것이 없는 자리</b>고,
     * 40분짜리 조사가 끝난 화면에 다시 와서 눌러야 하는 것이 유일한 일이었다.
     *
     * <p>이제 고리는 이 워커 안에서 둘 다 닫힌다:
     * <b>FULL 채택 → BM 큐 → BM 채택 → 다듬기 라운드 1</b>. 사람이 누르는 것은
     * 시장조사 실행 하나뿐이다.
     */
    static final String BUSINESS_MODEL_TRIGGER_SUBJECT = "MARKET_RESEARCH_FULL";

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(MarketResearchWorker.class);

    private final TaskRunService service;
    private final MarketResearchService completion;
    private final ConceptRefinementService refinement;
    private final JobEventPublisher events;
    private final InternalAiExecutionClient client;
    private final ObjectMapper mapper;

    public MarketResearchWorker(TaskRunService service, InternalAiExecutionClient client,
                                MarketResearchService completion, ConceptRefinementService refinement,
                                JobEventPublisher events, ObjectMapper mapper) {
        this.service = service;
        this.client = client;
        this.completion = completion;
        this.refinement = refinement;
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
            startBusinessModel(context);
            startRefinement(context);
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

    /**
     * <b>시장조사가 끝나면 BM 을 이어 건다.</b> (2026-08-16)
     *
     * <p>왜 채택 <b>뒤</b>인가. {@code startBm} 이 «가장 최근 FULL 판»을 근거로 삼는데
     * 그 판을 만드는 것이 바로 위의 {@code completion.complete} 다. 그 앞에서 부르면
     * 직전 판을 근거로 삼거나 {@code RESOURCE_NOT_FOUND} 로 죽는다.
     *
     * <p><b>멱등키를 FULL 의 taskRunId 로 못 박는다.</b> 워커가 같은 실행을 두 번 지나가도
     * ({@code createWithDisposition} 의 재생 경로) BM 은 한 번만 큐에 든다 —
     * 여기서 틀리면 <b>두 번 태운다</b>.
     *
     * <p>⚠ 예외를 <b>삼킨다.</b> 시장조사 채택은 이미 커밋됐고, 여기서 던지면 성공한
     * 40분짜리 실행이 실패로 뒤집혀 재시도 대상이 된다 — 지불한 수집을 통째로 잃는다.
     * 못 걸렸을 때의 문은 BM 화면의 「캔버스 만들기」 버튼으로 열려 있다.
     */
    private void startBusinessModel(com.aivle.backend.taskrun.service.TaskRunWorkerContext context) {
        if (!BUSINESS_MODEL_TRIGGER_SUBJECT.equals(context.subjectType())) return;
        String key = "auto-bm-" + context.taskRunId();
        try {
            var queued = completion.startBm(context.ownerId(), context.projectId(), key, key);
            log.info("Business model queued projectId={} marketTaskRunId={} bmTaskRunId={}",
                context.projectId(), context.taskRunId(), queued.taskRunId());
        } catch (RuntimeException failure) {
            log.warn("Business model chain failed projectId={} marketTaskRunId={}",
                context.projectId(), context.taskRunId(), failure);
        }
    }

    /**
     * <b>컨셉 다듬기 루프의 첫 문을 여기서 연다.</b> (2026-08-16)
     *
     * <p>{@code ConceptRefinementService.startFirstRound} 를 부르는 프로덕션 코드가
     * <b>한 곳도 없었다</b> — 시장조사가 끝나도 루프가 시작되지 않아 화면은 영원히
     * 「아직 안 함」이었다. 라운드 2 이상은 {@code ConceptRefinementWorker} 가 밀지만,
     * 그쪽은 <b>이미 있는 라운드</b>만 본다.
     *
     * <p>왜 채택 <b>뒤</b>인가. 다듬기의 재료는 조사 결과다. 채택이 커밋된 뒤에 부르면
     * (1) 방금 만든 판을 확실히 읽고 (2) 여기서 무엇이 터져도 <b>이미 지불한 수집 결과는
     * 그대로 남는다.</b> 그래서 예외를 <b>삼킨다</b> — 다시 걸 길은 사용자의 「다른 제안 받기」
     * ({@code retryRound})로 열려 있다.
     *
     * <p>⚠ 사유는 <b>로그로만</b> 보낸다. 화면에 띄우려면 화이트리스트를 지나야 한다.
     */
    private void startRefinement(com.aivle.backend.taskrun.service.TaskRunWorkerContext context) {
        if (!REFINEMENT_TRIGGER_SUBJECT.equals(context.subjectType())) return;
        try {
            refinement.startFirstRoundAfterResearch(context.projectId()).ifPresent(task ->
                log.info("Concept refinement round queued projectId={} marketTaskRunId={} refineTaskRunId={}",
                    context.projectId(), context.taskRunId(), task.getId()));
        } catch (RuntimeException failure) {
            // 채택은 이미 끝났다. 여기서 던지면 성공한 실행이 실패로 뒤집힌다.
            log.warn("Concept refinement bootstrap failed projectId={} marketTaskRunId={}",
                context.projectId(), context.taskRunId(), failure);
        }
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
