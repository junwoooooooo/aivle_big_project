package com.aivle.backend.pipeline.marketinterview;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskType;
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
    static final String SCHEMA_VERSION = "1.0";
    static final int MAX_ATTEMPTS = 3;

    private final ProjectRepository projects;
    private final ConceptPortfolioSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository seeds;
    private final BmPlanPreparationService bmPlans;
    private final MarketInterviewInputFactory inputs;
    private final MarketInterviewRunRepository runs;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher hasher;
    private final ObjectMapper mapper;

    public MarketInterviewService(ProjectRepository projects,
            ConceptPortfolioSelectionRepository selections,
            MarketAnalysisSeedSnapshotRepository seeds, BmPlanPreparationService bmPlans,
            MarketInterviewInputFactory inputs, MarketInterviewRunRepository runs,
            TaskRunService taskRuns, CanonicalInputHasher hasher, ObjectMapper mapper) {
        this.projects = projects; this.selections = selections; this.seeds = seeds; this.bmPlans = bmPlans;
        this.inputs = inputs; this.runs = runs; this.taskRuns = taskRuns; this.hasher = hasher; this.mapper = mapper;
    }

    @Transactional
    public CurrentView start(Long ownerId, Long projectId, String idempotencyKey, String correlationId) {
        Project project = owned(ownerId, projectId);
        Source source = currentSource(projectId);
        return create(ownerId, project, source, 1, idempotencyKey, correlationId);
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
        String rebuilt = inputs.build(source.seed(), source.selection(), source.bm());
        String rebuiltHash = hasher.hash(TaskType.MARKET_INTERVIEW, SCHEMA_VERSION, "ko-KR", rebuilt);
        if (!previous.getInputHash().equals(rebuiltHash)) {
            previous.markStale(previous.getResultJson(), LocalDateTime.now());
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "시장 인터뷰 입력 기준이 변경되었습니다.");
        }
        return create(ownerId, project, source, previous.getAttempt() + 1, idempotencyKey, correlationId);
    }

    @Transactional
    public CurrentView current(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        MarketInterviewRun run = runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElse(null);
        if (run == null) return CurrentView.notStarted();
        Source source = currentSourceOrNull(projectId);
        boolean stale = source == null || !bound(run, source);
        if (stale && run.getState() != MarketInterviewRun.State.STALE) {
            run.markStale(run.getResultJson(), LocalDateTime.now());
        }
        return view(run, stale);
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

    private CurrentView create(Long ownerId, Project project, Source source, int attempt,
            String idempotencyKey, String correlationId) {
        String input = inputs.build(source.seed(), source.selection(), source.bm());
        String inputHash = hasher.hash(TaskType.MARKET_INTERVIEW, SCHEMA_VERSION, "ko-KR", input);
        var created = taskRuns.createWithDisposition(ownerId, project.getId(), TaskType.MARKET_INTERVIEW,
            "MARKET_INTERVIEW", String.valueOf(project.getId()), input, inputHash,
            idempotencyKey, correlationId, 1);
        MarketInterviewRun domain = created.createdNew()
            ? runs.save(MarketInterviewRun.create(project, created.taskRun(), source.seed().getId(),
                source.selection().getId(), source.selection().getHypothesisRevision(), source.bm().revision(),
                attempt, idempotencyKey, inputHash, LocalDateTime.now()))
            : runs.findByTaskRunIdAndDeletedAtIsNull(created.taskRun().getId())
                .orElseThrow(() -> new IllegalStateException("Market interview TaskRun replay lineage missing"));
        return view(domain, false);
    }

    private Source currentSource(Long projectId) {
        Source value = currentSourceOrNull(projectId);
        if (value == null) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
            "현재 확정된 사업안으로 시장 인터뷰를 시작할 수 없습니다.");
        return value;
    }

    private Source currentSourceOrNull(Long projectId) {
        ConceptPortfolioSelection selection = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        if (selection == null) return null;
        MarketAnalysisSeedSnapshot seed = seeds
            .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId())
            .filter(value -> value.getProjectId().equals(projectId))
            .filter(value -> "CONCEPT_PORTFOLIO_V2".equals(value.getSourceType()))
            .orElse(null);
        return seed == null ? null : new Source(selection, seed, bmPlans.current(projectId));
    }

    private boolean bound(MarketInterviewRun run, Source source) {
        return run.getSourceMarketSeedSnapshotId().equals(source.seed().getId())
            && run.getSourceSelectionId().equals(source.selection().getId())
            && run.getSourceSelectionRevision() == source.selection().getHypothesisRevision()
            && run.getSourceBmPlanRevision() == source.bm().revision();
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
    }

    private CurrentView view(MarketInterviewRun run, boolean stale) {
        JsonNode result = run.getResultJson() == null ? null : mapper.readTree(run.getResultJson());
        String failure = run.getState() == MarketInterviewRun.State.FAILED
            ? "시장 인터뷰를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요." : null;
        return new CurrentView(run.getState().name(), stale,
            run.getSourceMarketSeedSnapshotId(), run.getSourceSelectionRevision(), run.getAttempt(),
            result, failure, run.getStartedAt(), run.getCompletedAt());
    }

    private record Source(ConceptPortfolioSelection selection, MarketAnalysisSeedSnapshot seed,
                          BmPlanPreparationService.PlanView bm) { }

    public record CurrentView(String state, boolean stale, String sourceMarketSeedSnapshotId,
            Integer sourceSelectionRevision, Integer attempt, JsonNode result, String failure,
            LocalDateTime startedAt, LocalDateTime completedAt) {
        static CurrentView notStarted() {
            return new CurrentView("NOT_STARTED", false, null, null, null, null, null, null, null);
        }
    }
}
