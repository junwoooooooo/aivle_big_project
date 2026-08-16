package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Source;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 패널 트윈 조사. <b>패턴 B</b> — 큐에 넣고 {@link TwinSurveyWorker} 가 돌린다.
 *
 * <p>{@link MarketResearchService} 와 같은 구조다: {@code start} 는 TaskRun 만 만들고,
 * 상태 전이와 결과 materialization은 worker 완료 트랜잭션에서 수행한다.
 * {@code current()} 는 canonical 상태를 읽기만 한다.
 */
@Service
public class TwinSurveyService {

    private static final Logger log = LoggerFactory.getLogger(TwinSurveyService.class);
    private static final String SCHEMA_VERSION = "1.0";
    private static final int MAX_ATTEMPTS = 3;
    /** 화면이 고르는 세 값. MDE 표가 이 셋으로만 실측돼 있다 — 임의 정수를 받으면 표에 없는 한계로 답하게 된다. */
    private static final Set<Integer> SAMPLE_SIZES = Set.of(50, 100, 300);

    private final ProjectRepository projects;
    private final CurrentConceptSourceResolver sources;
    private final TwinSurveyRunRepository runs;
    private final TwinSurveyVersionRepository versions;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher hasher;
    private final TwinSurveyInputFactory inputs;
    private final ObjectMapper mapper;

    public TwinSurveyService(ProjectRepository projects, CurrentConceptSourceResolver sources,
                             TwinSurveyRunRepository runs,
                             TwinSurveyVersionRepository versions,
                             TaskRunService taskRuns, CanonicalInputHasher hasher,
                             TwinSurveyInputFactory inputs, ObjectMapper mapper) {
        this.projects = projects; this.sources = sources;
        this.runs = runs; this.versions = versions;
        this.taskRuns = taskRuns; this.hasher = hasher;
        this.inputs = inputs; this.mapper = mapper;
    }

    @Transactional
    public RunView start(Long ownerId, Long projectId, String situation, JsonNode pairs, int sampleSize,
                         String idempotencyKey, String correlationId) {
        Project project = owned(ownerId, projectId);
        Source source = sources.require(projectId,
            "현재 확정된 사업안으로 트윈 패널 조사를 시작할 수 없습니다.");
        if (!SAMPLE_SIZES.contains(sampleSize)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "표본 크기는 50·100·300 중 하나다 — 다른 값은 측정 한계를 표기할 수 없다");
        }
        if (pairs == null || !pairs.isArray() || pairs.isEmpty() || pairs.size() > 4) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "비교 쌍은 1~4개다");
        }
        String input = inputs.build(source, situation, pairs, sampleSize);
        String inputHash = hasher.hash(TaskType.TWIN_SURVEY, SCHEMA_VERSION, "ko-KR", input);
        // 「누를 때마다 새로 실행」이라 같은 자극이면 canonicalInputHash 가 같다 — nonce 가 없으면
        // 중복 방지에 걸려 두 번째 실행이 만들어지지 않는다. 시장조사·마케팅과 같은 이유다.
        // ⚠ subjectId 는 **NOT NULL 이다**. 컴파일도 단위 테스트도 이것을 못 잡는다 —
        //   실스택 스모크가 첫 POST 에서 500 으로 잡아냈다. 트윈 조사의 주체는 컨셉이 아니라
        //   프로젝트다(자극을 사용자가 그 자리에서 만든다).
        var created = taskRuns.createWithDisposition(ownerId, project.getId(), TaskType.TWIN_SURVEY,
            "TWIN_SURVEY", String.valueOf(project.getId()), input, inputHash,
            idempotencyKey, correlationId, 1);
        TwinSurveyRun domain = created.createdNew()
            ? runs.save(TwinSurveyRun.create(project, created.taskRun(), inputHash, sampleSize,
                source.seed().getId(), source.selection().getId(), source.selection().getHypothesisRevision(),
                source.bm().revision(), 1))
            : runs.findByTaskRunIdAndDeletedAtIsNull(created.taskRun().getId())
                .orElseThrow(() -> new IllegalStateException("Twin TaskRun replay lineage missing"));
        return runView(domain);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public RunView retry(Long ownerId, Long projectId, String idempotencyKey, String correlationId) {
        Project project = owned(ownerId, projectId);
        TwinSurveyRun previous = runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED));
        if (previous.getState() != TwinSurveyRun.State.FAILED || previous.getAttempt() >= MAX_ATTEMPTS)
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        Source source = sources.require(projectId,
            "현재 확정된 사업안으로 트윈 패널 조사를 재시도할 수 없습니다.");
        if (!bound(previous, source)) {
            previous.markStale();
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "사업안이 변경되어 이전 트윈 패널 조사를 재시도할 수 없습니다.");
        }
        String input = previous.getTaskRun().getInputSnapshot();
        String inputHash = hasher.hash(TaskType.TWIN_SURVEY, SCHEMA_VERSION, "ko-KR", input);
        if (!previous.getInputSnapshotHash().equals(inputHash)) {
            previous.markStale();
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "트윈 패널 조사 입력 기준이 변경되었습니다.");
        }
        var created = taskRuns.createWithDisposition(ownerId, project.getId(), TaskType.TWIN_SURVEY,
            "TWIN_SURVEY", String.valueOf(project.getId()), input, inputHash,
            idempotencyKey, correlationId, 1);
        TwinSurveyRun domain = created.createdNew()
            ? runs.save(TwinSurveyRun.create(project, created.taskRun(), inputHash, previous.getSampleSize(),
                source.seed().getId(), source.selection().getId(), source.selection().getHypothesisRevision(),
                source.bm().revision(), previous.getAttempt() + 1))
            : runs.findByTaskRunIdAndDeletedAtIsNull(created.taskRun().getId())
                .orElseThrow(() -> new IllegalStateException("Twin retry TaskRun replay lineage missing"));
        return runView(domain);
    }

    /** Canonical Twin current projection. Broken lineage is durably marked historical. */
    @Transactional
    public CurrentView current(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        Source source = sources.currentOrNull(projectId);
        TwinSurveyRun run = runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElse(null);
        if (run == null) return new CurrentView(null, null, false);
        TwinSurveyVersion version = versions.findBySourceRunIdAndDeletedAtIsNull(run.getId()).orElse(null);
        boolean stale = run.getState() == TwinSurveyRun.State.STALE || source == null || !bound(run, source);
        if (stale && run.getState() != TwinSurveyRun.State.STALE) run.markStale();
        return new CurrentView(runView(run), version == null ? null : versionView(version), stale);
    }

    @Transactional
    public void markRunning(String taskRunId) {
        runs.findByTaskRunIdAndDeletedAtIsNull(taskRunId).ifPresent(run -> { run.running(); runs.save(run); });
    }

    @Transactional
    public void complete(TaskRunService.Claim claim, ExecutionResponse response) {
        TwinSurveyRun run = runs.findByTaskRunIdAndDeletedAtIsNull(claim.taskRunId())
            .orElseThrow(() -> new IllegalStateException("Twin run missing"));
        if (versions.findBySourceRunIdAndDeletedAtIsNull(run.getId()).isPresent()) return;
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
        materialize(run, response.result());
        Source source = sources.currentOrNull(run.getProject().getId());
        if (source == null || !bound(run, source)) run.markStale();
        else { run.running(); run.succeed(); }
        runs.save(run);
    }

    @Transactional
    public void materializeFailure(String taskRunId, String code) {
        runs.findByTaskRunIdAndDeletedAtIsNull(taskRunId).ifPresent(run -> {
            if (run.getState() != TwinSurveyRun.State.SUCCEEDED
                    && run.getState() != TwinSurveyRun.State.FAILED
                    && run.getState() != TwinSurveyRun.State.STALE) {
                Source source = sources.currentOrNull(run.getProject().getId());
                if (source == null || !bound(run, source)) run.markStale();
                else run.fail(code);
                runs.save(run);
            }
        });
    }

    private void materialize(TwinSurveyRun run, JsonNode result) {
        int pairCount = result.path("pairs").size();
        int measurable = 0;
        int caveatCount = 0;
        for (JsonNode pair : result.path("pairs")) {
            if (pair.path("measurable").asBoolean(false)) measurable++;
            caveatCount += pair.path("caveats").size();
        }
        int number = Math.toIntExact(versions.countByProjectIdAndDeletedAtIsNull(run.getProject().getId()) + 1);
        versions.save(TwinSurveyVersion.of(run.getProject(), run, number, result.toString(),
            new TwinSurveyVersion.Summary(result.path("sampleSize").asInt(), pairCount, measurable, caveatCount)));
        if (measurable == 0 && pairCount > 0) {
            // 실패가 아니다 — 「못 잼」은 정직한 산출이다. 다만 조용히 지나가지 않는다:
            // 표본을 키우면 잴 수도 있다는 사실을 운영이 알아야 한다.
            log.info("Twin survey measured nothing projectId={} runId={} pairs={} sampleSize={}",
                run.getProject().getId(), run.getId(), pairCount, run.getSampleSize());
        }
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "프로젝트를 찾을 수 없다"));
    }

    private boolean bound(TwinSurveyRun run, Source source) {
        return run.getSourceMarketSeedSnapshotId().equals(source.seed().getId())
            && run.getSourcePortfolioSelectionId().equals(source.selection().getId())
            && run.getSourceSelectionRevision() == source.selection().getHypothesisRevision()
            && run.getSourceBmPlanRevision() == source.bm().revision();
    }

    private RunView runView(TwinSurveyRun run) {
        return new RunView(run.getId(), run.getState().name(), run.getSampleSize(), run.getAttempt(),
            run.getSourceMarketSeedSnapshotId(), run.getSourcePortfolioSelectionId(),
            run.getSourceSelectionRevision(), run.getSourceBmPlanRevision(),
            run.getTaskRun().getId(), run.getTaskRun().getState().name(),
            run.getErrorCode(), run.getState() == TwinSurveyRun.State.FAILED
                && run.getAttempt() < MAX_ATTEMPTS);
    }

    private VersionView versionView(TwinSurveyVersion version) {
        return new VersionView(version.getId(), version.getVersionNumber(),
            mapper.readTree(version.getResultJson()), version.getSampleSize(),
            version.getPairCount(), version.getMeasurableCount(), version.getCaveatCount());
    }

    public record RunView(Long id, String state, Integer sampleSize, Integer attempt,
                          String sourceMarketSeedSnapshotId, Long sourceSelectionId,
                          Integer sourceSelectionRevision, Integer sourceBmPlanRevision,
                          String taskRunId, String taskState,
                          String errorCode, boolean retryable) { }

    /** {@code result} 는 계약 그대로다 — 백엔드가 다시 가공하지 않는다. */
    public record VersionView(Long id, Integer versionNumber, JsonNode result, Integer sampleSize,
                              Integer pairCount, Integer measurableCount, Integer caveatCount) { }

    public record CurrentView(RunView run, VersionView version, boolean stale) { }
}
