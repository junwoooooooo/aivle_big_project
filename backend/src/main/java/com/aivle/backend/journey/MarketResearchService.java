package com.aivle.backend.journey;

import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskResult;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 시장조사(1단계) · BM 캔버스(2단계). <b>패턴 B</b> — 큐에 넣고 워커가 돌린다.
 *
 * <p>{@link LegalPrecheckService} 의 구조를 따른다: {@code start} 는 TaskRun 만 만들고,
 * 상태 전이는 {@code current()} 가 불릴 때 {@link #synchronize} 가 한다.
 * <b>지연 반영이라 화면이 폴링해야 전이한다</b> — 패턴 B 에서 가장 헷갈리는 지점이다.
 */
@Service
public class MarketResearchService {

    private static final Logger log = LoggerFactory.getLogger(MarketResearchService.class);
    private static final String SCHEMA_VERSION = "1.0";

    private final ProjectRepository projects;
    private final MarketResearchRunRepository runs;
    private final MarketResearchVersionRepository versions;
    private final TaskResultRepository taskResults;
    private final TaskRunService taskRuns;
    private final com.aivle.backend.taskrun.service.CanonicalInputHasher hasher;
    private final MarketResearchInputFactory inputs;
    private final ObjectMapper mapper;

    public MarketResearchService(ProjectRepository projects, MarketResearchRunRepository runs,
                                 MarketResearchVersionRepository versions, TaskResultRepository taskResults,
                                 TaskRunService taskRuns,
                                 com.aivle.backend.taskrun.service.CanonicalInputHasher hasher,
                                 MarketResearchInputFactory inputs, ObjectMapper mapper) {
        this.projects = projects; this.runs = runs; this.versions = versions;
        this.taskResults = taskResults; this.taskRuns = taskRuns; this.hasher = hasher;
        this.inputs = inputs; this.mapper = mapper;
    }

    /** 1단계 — 시장조사 전 구간. 90~266초 걸린다. */
    @Transactional
    public RunView startFull(Long ownerId, Long projectId, JsonNode concept, String conceptId, String asOf) {
        Project project = owned(ownerId, projectId);
        String input = inputs.full(concept, conceptId, asOf);
        return start(ownerId, project, MarketResearchRun.Kind.FULL, null, input, conceptId);
    }

    /**
     * 2단계 — 「다음」을 눌렀을 때. 1단계 결과를 근거로 캔버스를 만든다.
     *
     * <p>1단계가 성공해 있어야 한다. 없으면 근거 없는 캔버스가 되고, 그건 이 파이프라인이
     * 없애려는 실패 그 자체다.
     */
    @Transactional
    public RunView startBm(Long ownerId, Long projectId, String asOf) {
        Project project = owned(ownerId, projectId);
        MarketResearchVersion source = versions
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, MarketResearchRun.Kind.FULL)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "시장조사 결과가 없다 — 1단계를 먼저 실행해야 한다"));
        // ⚠ **1단계가 쓴 이름표를 그대로 잇는다.** 클라이언트가 보낸 conceptId 로 덮지 않는다 —
        //    1단계와 다른 컨셉으로 판정하면 「관측은 A, 잣대는 B」가 되고, 그것이
        //    되짚기가 카페 컨셉을 집던 사고와 같은 종류다.
        //    이전에는 결과의 runId 를 sourceRun 으로 넘겼는데 그건 taskAttemptId 이지
        //    runs/ 밑 디렉터리가 아니라서 AI 쪽이 400 을 냈다. 원장은 이름표가 정한다.
        String label = mapper.readTree(source.getResultJson()).path("conceptId").asText();
        if (label == null || label.isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "1단계 결과에 conceptId 가 없다 — 이어붙일 컨셉을 알 수 없다");
        }
        String input = inputs.bm(label, asOf);
        return start(ownerId, project, MarketResearchRun.Kind.BM, source.getSourceRun(), input, label);
    }

    private RunView start(Long ownerId, Project project, MarketResearchRun.Kind kind,
                          MarketResearchRun sourceRun, String inputJson, String conceptId) {
        String inputHash = hasher.hash(TaskType.MARKET_RESEARCH, SCHEMA_VERSION, "ko-KR", inputJson);
        // ⚠ **nonce 가 필요하다.** 「누를 때마다 새로 실행」이라 같은 컨셉이면 canonicalInputHash 가
        //    같고, `idx_task_runs_active_conflict` 와 `TaskRunService.create` 의 중복 방지에 걸린다.
        //    마케팅 리포트가 같은 이유로 UUID 를 쓴다.
        String nonce = UUID.randomUUID().toString();
        TaskRun task = taskRuns.create(ownerId, project.getId(), TaskType.MARKET_RESEARCH,
            "MARKET_RESEARCH_" + kind.name(), conceptId, inputJson, inputHash, nonce, nonce, 1);
        return runView(runs.save(MarketResearchRun.create(project, kind, sourceRun, task, inputHash)));
    }

    /** 화면이 폴링하는 자리. <b>여기서 상태가 전이한다.</b> */
    @Transactional
    public CurrentView current(Long ownerId, Long projectId, MarketResearchRun.Kind kind) {
        owned(ownerId, projectId);
        MarketResearchRun run = runs
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, kind)
            .orElse(null);
        if (run == null) return new CurrentView(null, null);
        synchronize(run);
        MarketResearchVersion version = versions.findBySourceRunIdAndDeletedAtIsNull(run.getId()).orElse(null);
        return new CurrentView(runView(run), version == null ? null : versionView(version));
    }

    private void synchronize(MarketResearchRun run) {
        TaskRun task = run.getTaskRun();
        TaskRunState state = task.getState();
        if (state == TaskRunState.QUEUED || state == TaskRunState.READY) return;
        if (state == TaskRunState.RUNNING) { run.running(); runs.save(run); return; }
        if (state == TaskRunState.FAILED || state == TaskRunState.TIMED_OUT
            || state == TaskRunState.CANCELLED) {
            if (run.getState() != MarketResearchRun.State.FAILED) {
                log.warn("Market research task failed projectId={} runId={} kind={} taskRunId={} errorCode={} retryable={}",
                    run.getProject().getId(), run.getId(), run.getKind(), task.getId(),
                    task.getLastErrorCode(), task.isRetryable());
                run.fail(task.getLastErrorCode());
                runs.save(run);
            }
            return;
        }
        if (state != TaskRunState.SUCCEEDED
            || versions.findBySourceRunIdAndDeletedAtIsNull(run.getId()).isPresent()) return;
        TaskResult result = task.getFinalResultId() == null ? null
            : taskResults.findById(task.getFinalResultId()).orElse(null);
        if (result == null) return;
        materialize(run, mapper.readTree(result.getResultJson()));
        run.succeed();
        runs.save(run);
    }

    /**
     * <b>결과를 쪼개지 않는다.</b> 통째로 저장하고 목록용 스칼라만 따로 센다.
     *
     * <p>{@code caveatCount} 를 세는 이유는 <b>경계가 0으로 떨어지는 것을 눈으로 보기 위해서다</b>.
     * JSON 안에 묻혀 있으면 아무도 안 본다.
     */
    private void materialize(MarketResearchRun run, JsonNode result) {
        int evidenceCount = result.path("evidence").size();
        int caveatCount = 0;
        for (JsonNode item : result.path("evidence")) caveatCount += item.path("caveats").size();
        for (JsonNode cell : result.path("canvas").path("cells")) caveatCount += cell.path("caveats").size();

        Integer filled = null, partial = null, missing = null;
        if (result.path("scorecard").isArray()) {
            filled = 0; partial = 0; missing = 0;
            for (JsonNode item : result.get("scorecard")) {
                switch (item.path("state").asText()) {
                    case "FILLED" -> filled++;
                    case "PARTIAL" -> partial++;
                    case "MISSING" -> missing++;
                    default -> { }        // REPORTED(⑦행)는 성적에 안 센다
                }
            }
        }
        JsonNode bm = result.path("bm");
        MarketResearchVersion.Summary summary = new MarketResearchVersion.Summary(
            filled, partial, missing,
            bm.isObject() ? bm.path("decision").asText(null) : null,
            bm.isObject() ? bm.path("confidence").asText(null) : null,
            evidenceCount, caveatCount);

        int number = Math.toIntExact(versions.countByProjectIdAndKindAndDeletedAtIsNull(
            run.getProject().getId(), run.getKind()) + 1);
        versions.save(MarketResearchVersion.of(run.getProject(), run, number,
            result.toString(), summary));
        if (caveatCount == 0 && evidenceCount > 0) {
            // 근거가 있는데 경계가 하나도 없으면 **소실을 의심해야 한다**. 막지는 않는다 —
            // 경계가 진짜로 없는 관측도 있다. 다만 조용히 지나가지 않는다.
            log.warn("Market research result has evidence but no caveats projectId={} runId={} kind={} evidence={}",
                run.getProject().getId(), run.getId(), run.getKind(), evidenceCount);
        }
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "프로젝트를 찾을 수 없다"));
    }

    private RunView runView(MarketResearchRun run) {
        return new RunView(run.getId(), run.getKind().name(), run.getState().name(),
            run.getTaskRun().getId(), run.getTaskRun().getState().name(),
            run.getErrorCode(), safeErrorReason(run.getTaskRun().getLastErrorReason()), run.getTaskRun().isRetryable());
    }

    /** Only contract-level reasons are returned; provider details and input text stay server-side. */
    private String safeErrorReason(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "FIELD_CONSTRAINT_VIOLATION", "HASH_MISMATCH", "UNKNOWN_FIELD",
                 "CHUNK_COUNT_EXCEEDED", "CHUNK_SEQUENCE_INVALID", "REQUEST_CONTRACT_INVALID",
                 "REQUEST_DEADLINE_EXCEEDED", "AI_CONFIGURATION_INVALID" -> reason;
            default -> null;
        };
    }

    private VersionView versionView(MarketResearchVersion version) {
        return new VersionView(version.getId(), version.getKind().name(), version.getVersionNumber(),
            mapper.readTree(version.getResultJson()),
            version.getEvidenceCount(), version.getCaveatCount(),
            version.getDecision(), version.getConfidence(),
            version.getFilledCount(), version.getPartialCount(), version.getMissingCount());
    }

    public record RunView(Long id, String kind, String state, String taskRunId, String taskState,
                          String errorCode, String errorReason, boolean retryable) { }

    /** {@code result} 는 계약 그대로다 — 백엔드가 다시 가공하지 않는다. */
    public record VersionView(Long id, String kind, Integer versionNumber, JsonNode result,
                              Integer evidenceCount, Integer caveatCount,
                              String decision, String confidence,
                              Integer filledCount, Integer partialCount, Integer missingCount) { }

    public record CurrentView(RunView run, VersionView version) { }
}
