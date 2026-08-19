package com.aivle.backend.pipeline.marketinterview;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.marketinterview.MarketInterviewSourceResolver.Source;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskResult;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class MarketInterviewService {
    static final String TASK_SCHEMA_VERSION = "1.0";
    static final int MAX_ATTEMPTS = 3;

    private final ProjectRepository projects;
    private final MarketInterviewSourceResolver sources;
    private final MarketInterviewInputFactory inputs;
    private final MarketInterviewRunRepository runs;
    private final TaskResultRepository taskResults;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher hasher;
    private final ObjectMapper mapper;

    public MarketInterviewService(ProjectRepository projects,
            MarketInterviewSourceResolver sources,
            MarketInterviewInputFactory inputs, MarketInterviewRunRepository runs,
            TaskResultRepository taskResults, TaskRunService taskRuns,
            CanonicalInputHasher hasher, ObjectMapper mapper) {
        this.projects = projects; this.sources = sources;
        this.inputs = inputs; this.runs = runs; this.taskResults = taskResults;
        this.taskRuns = taskRuns; this.hasher = hasher; this.mapper = mapper;
    }

    @Transactional
    public CurrentView start(Long ownerId, Long projectId, String idempotencyKey, String correlationId) {
        Source source = currentSource(projectId);
        return start(ownerId, projectId, idempotencyKey, correlationId, inputs.board(source), 20);
    }

    @Transactional
    public CurrentView start(Long ownerId, Long projectId, String idempotencyKey, String correlationId,
            int sampleSize) {
        Source source = currentSource(projectId);
        return start(ownerId, projectId, idempotencyKey, correlationId, inputs.board(source), sampleSize);
    }

    @Transactional
    public CurrentView start(Long ownerId, Long projectId, String idempotencyKey, String correlationId,
            JsonNode conceptBoard, int sampleSize) {
        Project project = owned(ownerId, projectId);
        Source source = currentSource(projectId);
        return create(ownerId, project, source, conceptBoard, sampleSize, 1, idempotencyKey, correlationId);
    }

    @Transactional(readOnly = true)
    public JsonNode board(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        return inputs.board(currentSource(projectId));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public CurrentView retry(Long ownerId, Long projectId, String idempotencyKey, String correlationId) {
        Project project = owned(ownerId, projectId);
        MarketInterviewRun previous = runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED));
        if (previous.getState() != MarketInterviewRun.State.FAILED || previous.getAttempt() >= MAX_ATTEMPTS)
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        Source source = currentSource(projectId);
        if (!bound(previous, source)) {
            previous.markStale(previous.getResultJson(), LocalDateTime.now());
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "사업안이 변경되어 이전 시장 인터뷰를 재시도할 수 없습니다.");
        }
        Integer sampleSize = previous.getRequestedSampleSize();
        if (sampleSize == null) {
            previous.markStale(previous.getResultJson(), LocalDateTime.now());
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "이전 형식의 시장 인터뷰입니다. 현재 사업안으로 새 인터뷰를 시작해 주세요.");
        }
        JsonNode previousInput = mapper.readTree(previous.getTaskRun().getInputSnapshot());
        JsonNode conceptBoard = previousInput.path("conceptBoard");
        String rebuilt = inputs.build(source, conceptBoard, sampleSize);
        String rebuiltHash = hasher.hash(TaskType.MARKET_INTERVIEW, TASK_SCHEMA_VERSION, "ko-KR", rebuilt);
        if (!previous.getInputHash().equals(rebuiltHash)) {
            previous.markStale(previous.getResultJson(), LocalDateTime.now());
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "시장 인터뷰 입력 기준이 변경되었습니다.");
        }
        return create(ownerId, project, source, conceptBoard, sampleSize, previous.getAttempt() + 1,
            idempotencyKey, correlationId);
    }

    @Transactional
    public CurrentView current(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        MarketInterviewRun run = runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElse(null);
        Source source = currentSourceOrNull(projectId);
        JsonNode preview = source == null ? null : inputs.preview(source);
        if (run == null) return CurrentView.notStarted(preview);
        synchronize(run);
        boolean stale = source == null || !bound(run, source);
        if (stale && run.getState() != MarketInterviewRun.State.STALE) {
            run.markStale(run.getResultJson(), LocalDateTime.now());
        }
        return view(run, stale, preview);
    }

    /**
     * MAIN worker owns execution and TaskResult adoption. This outer projection only mirrors that
     * terminal state into the FULL lineage row; it never changes the MAIN result envelope.
     */
    private void synchronize(MarketInterviewRun run) {
        if (run.getState() != MarketInterviewRun.State.RUNNING) return;
        TaskRun task = run.getTaskRun();
        TaskRunState state = task.getState();
        if (state == TaskRunState.FAILED || state == TaskRunState.TIMED_OUT
                || state == TaskRunState.CANCELLED) {
            run.fail(task.getLastErrorCode(), LocalDateTime.now());
            return;
        }
        if (state != TaskRunState.SUCCEEDED || task.getFinalResultId() == null) return;
        TaskResult result = taskResults.findById(task.getFinalResultId()).orElse(null);
        if (result != null) run.succeed(result.getResultJson(), LocalDateTime.now());
    }

    @Transactional
    public void complete(TaskRunService.Claim claim, ExecutionResponse response) {
        MarketInterviewRun run = runs.findByTaskRunIdAndDeletedAtIsNull(claim.taskRunId())
            .orElseThrow(() -> new IllegalStateException("Market interview run missing"));
        String resultJson = mapper.writeValueAsString(response.result());
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            resultJson, response.canonicalInputHash(), response.resultSchemaVersion());
        Source source = currentSourceOrNull(run.getProject().getId());
        if (source == null || !bound(run, source)) run.markStale(resultJson, LocalDateTime.now());
        else run.succeed(resultJson, LocalDateTime.now());
    }

    @Transactional
    public void materializeFailure(String taskRunId, String code) {
        runs.findByTaskRunIdAndDeletedAtIsNull(taskRunId).ifPresent(run -> {
            if (run.getState() == MarketInterviewRun.State.RUNNING) run.fail(code, LocalDateTime.now());
        });
    }

    private CurrentView create(Long ownerId, Project project, Source source, JsonNode conceptBoard,
            int sampleSize, int attempt,
            String idempotencyKey, String correlationId) {
        String input = inputs.build(source, conceptBoard, sampleSize);
        String inputHash = hasher.hash(TaskType.MARKET_INTERVIEW, TASK_SCHEMA_VERSION, "ko-KR", input);
        var created = taskRuns.createWithDisposition(ownerId, project.getId(), TaskType.MARKET_INTERVIEW,
            "MARKET_INTERVIEW", String.valueOf(project.getId()), input, inputHash,
            idempotencyKey, correlationId, 1);
        MarketInterviewRun domain = created.createdNew()
            ? runs.save(MarketInterviewRun.create(project, created.taskRun(), source.refinementFinal().getId(), source.seed().getId(),
                source.selection().getId(), source.selection().getHypothesisRevision(), source.bm().revision(),
                sampleSize, attempt, idempotencyKey, inputHash, LocalDateTime.now()))
            : runs.findByTaskRunIdAndDeletedAtIsNull(created.taskRun().getId())
                .orElseThrow(() -> new IllegalStateException("Market interview TaskRun replay lineage missing"));
        return view(domain, false, inputs.preview(source));
    }

    private Source currentSource(Long projectId) {
        return sources.require(projectId);
    }

    private Source currentSourceOrNull(Long projectId) {
        return sources.currentOrNull(projectId);
    }

    private boolean bound(MarketInterviewRun run, Source source) {
        return java.util.Objects.equals(run.getSourceConceptRefinementFinalId(), source.refinementFinal().getId())
            && run.getSourceMarketSeedSnapshotId().equals(source.seed().getId())
            && run.getSourceSelectionId().equals(source.selection().getId())
            && run.getSourceSelectionRevision() == source.selection().getHypothesisRevision()
            && run.getSourceBmPlanRevision() == source.bm().revision();
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
    }

    private CurrentView view(MarketInterviewRun run, boolean stale, JsonNode preview) {
        JsonNode result = run.getResultJson() == null ? null : mapper.readTree(run.getResultJson());
        String failure = run.getState() != MarketInterviewRun.State.FAILED ? null
            : "MARKET_INTERVIEW_SEMANTIC_MISMATCH".equals(run.getFailureCode())
            ? "결과가 현재 사업안의 의미와 일치하지 않아 저장하지 않았습니다. 현재 사업안으로 다시 시도해 주세요."
            : "MARKET_INTERVIEW_TARGET_UNAVAILABLE".equals(run.getFailureCode())
            ? "현재 profile bank에서 직접 타겟을 구성할 수 없습니다. 타겟 조건을 확인해 주세요."
            : "RESULT_SCHEMA_INVALID".equals(run.getFailureCode())
            ? "응답 코딩 근거를 확인하는 단계에서 자동 재시도 후에도 결과 형식이 맞지 않았습니다. 새 실행으로 다시 시도해 주세요."
            : "시장 인터뷰를 완료하지 못했습니다. 실패한 단계를 확인한 뒤 다시 시도해 주세요.";
        return new CurrentView(run.getState().name(), stale,
            run.getSourceConceptRefinementFinalId(), run.getSourceMarketSeedSnapshotId(),
            run.getSourceSelectionRevision(), run.getAttempt(),
            run.getRequestedSampleSize(),
            result, failure, run.getFailureCode(), run.getTaskRun().getId(),
            preview == null ? null : preview.path("conceptBoard"),
            run.getStartedAt(), run.getCompletedAt(),
            run.getState() == MarketInterviewRun.State.FAILED && !stale && run.getAttempt() < MAX_ATTEMPTS,
            run.getState() == MarketInterviewRun.State.FAILED && preview != null);
    }

    public record CurrentView(String state, boolean stale, Long sourceConceptRefinementFinalId,
            String sourceMarketSeedSnapshotId,
            Integer sourceSelectionRevision, Integer attempt, Integer requestedSampleSize,
            JsonNode result, String failure, String failureCode, String taskRunId,
            JsonNode conceptBoard,
            LocalDateTime startedAt, LocalDateTime completedAt,
            boolean retryAllowed, boolean restartAllowed) {
        static CurrentView notStarted(JsonNode preview) {
            return new CurrentView("NOT_STARTED", false, null, null, null, null, null, null, null, null, null,
                preview == null ? null : preview.path("conceptBoard"), null, null, false, false);
        }
    }
}
