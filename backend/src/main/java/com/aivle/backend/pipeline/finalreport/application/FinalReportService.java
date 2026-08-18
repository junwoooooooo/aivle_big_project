package com.aivle.backend.pipeline.finalreport.application;

import static com.aivle.backend.pipeline.finalreport.api.FinalReportApiModels.*;
import static com.aivle.backend.pipeline.finalreport.application.FinalReportComposer.ReportSource;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationSession;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationSessionRepository;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Binding;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Source;
import com.aivle.backend.pipeline.finalreport.domain.FinalReportSnapshot;
import com.aivle.backend.pipeline.finalreport.repository.FinalReportSnapshotRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import com.aivle.backend.pipeline.launchreadiness.repository.LaunchReadinessInputSnapshotRepository;
import com.aivle.backend.pipeline.launchreadiness.repository.LaunchReadinessReportRepository;
import com.aivle.backend.pipeline.marketing.domain.MarketingContentStatus;
import com.aivle.backend.pipeline.marketing.repository.MarketingAssetRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRevisionRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketing.strategy.repository.MarketingStrategyReportRepository;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.marketinterview.MarketInterviewRun;
import com.aivle.backend.pipeline.marketinterview.MarketInterviewRunRepository;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse;
import com.aivle.backend.pipeline.module.ProjectModuleStatusService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.repository.UserRepository;
import com.aivle.backend.taskrun.domain.TaskResultValidationState;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.repository.TaskAttemptRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinalReportService {
    private static final int MANIFEST_SCHEMA_VERSION = 2;
    private static final List<String> OPTIONAL = List.of("MARKET_INTERVIEW", "MARKETING_STRATEGY",
        "MARKETING", "MARKETING_ASSETS", "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE", "FINANCE_REPORT");
    private static final java.util.Set<String> STRATEGY_CONTEXT_TYPES = java.util.Set.of(
        "CURRENT_CONCEPT", "MARKET", "BUSINESS_MODEL", "MARKET_INTERVIEW",
        "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE", "FINANCE_REPORT");

    private final ProjectRepository projects;
    private final UserRepository users;
    private final CurrentConceptSourceResolver currentConcepts;
    private final BusinessValidationSessionRepository validationSessions;
    private final MarketResearchVersionRepository marketVersions;
    private final MarketInterviewRunRepository marketInterviews;
    private final MarketingSourceSnapshotRepository marketingSources;
    private final MarketingContentRepository marketingContents;
    private final MarketingContentRevisionRepository marketingRevisions;
    private final MarketingAssetRepository marketingAssets;
    private final MarketingStrategyReportRepository marketingStrategies;
    private final LaunchReadinessInputSnapshotRepository launchInputs;
    private final LaunchReadinessReportRepository launchReports;
    private final FinancialInputSnapshotRepository financeSnapshots;
    private final TaskRunRepository taskRuns;
    private final TaskAttemptRepository taskAttempts;
    private final TaskResultRepository taskResults;
    private final TaskRunService taskRunService;
    private final CanonicalInputHasher inputHasher;
    private final JobEventPublisher events;
    private final ProjectModuleStatusService moduleStatuses;
    private final FinalReportSnapshotRepository snapshots;
    private final FinalReportComposer composer;
    private final ObjectMapper mapper;

    public FinalReportView current(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        FinalReportSnapshot snapshot = snapshots
            .findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId).orElse(null);
        if (snapshot == null) return draft(project, current, 1);
        return view(current.ready() && exact(snapshot, current) ? State.CURRENT : State.STALE, snapshot, current);
    }

    public FinalReportSnapshot requireSnapshot(Long ownerId, Long projectId, String snapshotId) {
        owned(ownerId, projectId);
        return snapshots.findByIdAndProjectIdAndDeletedAtIsNull(snapshotId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public State state(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        return snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(snapshot -> current.ready() && exact(snapshot, current) ? State.CURRENT : State.STALE)
            .orElse(current.ready() ? State.READY : State.NOT_READY);
    }

    public FinalReportStatusView status(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        FinalReportSnapshot snapshot = snapshots
            .findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId).orElse(null);
        TaskRun latest = taskRuns.findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, TaskType.FINAL_BUSINESS_PROPOSAL_GENERATION).orElse(null);
        TaskRun active = java.util.Optional.ofNullable(latest)
            .filter(task -> !java.util.Set.of("SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT", "NEEDS_INPUT")
                .contains(task.getState().name()))
            .orElse(null);
        State state = active != null ? State.GENERATING : snapshot == null ? (current.ready() ? State.READY : State.NOT_READY)
            : (current.ready() && exact(snapshot, current) ? State.CURRENT : State.STALE);
        List<String> available = current.sources().stream().map(ReportSource::type).distinct().toList();
        return new FinalReportStatusView(state, snapshot == null ? null : snapshot.getReportVersion(),
            snapshot == null ? null : snapshot.getGeneratedAt(), state == State.STALE,
            active == null ? null : active.getId(),
            current.blocking(), available, current.omitted(), sourceStates(current, projectId),
            latest == null ? null : latest.getId(), latest == null ? null : latest.getState().name(),
            latest == null ? null : latest.getLastErrorCode(), lastErrorReason(latest));
    }

    public CurrentSourceCatalog currentSourceCatalog(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        ArrayNode manifest = mapper.createArrayNode();
        current.manifest().path("sources").forEach(item -> manifest.add(item.deepCopy()));
        ObjectNode sourceData = mapper.createObjectNode();
        current.sources().forEach(source -> sourceData.set(source.type(), source.data().deepCopy()));
        return new CurrentSourceCatalog(manifest, sourceData, sourceStates(current, projectId),
            current.blocking(), current.omitted(), current.hash(), strategySourceHash(current.sources()));
    }

    private String lastErrorReason(TaskRun latest) {
        if (latest == null || latest.getCurrentAttemptId() == null) return null;
        return taskAttempts.findByIdAndTaskRunId(latest.getCurrentAttemptId(), latest.getId())
            .map(attempt -> attempt.getErrorReason()).orElse(null);
    }

    @Transactional
    public ProposalActionResponse startProposal(Long ownerId, Long projectId, String idempotencyKey,
            String correlationId, List<String> includedOptionalSources) {
        String key = requiredKey(idempotencyKey);
        Project project = owned(ownerId, projectId);
        SourceSet current = selectSources(sources(ownerId, project), includedOptionalSources);
        if (!current.ready()) throw new BusinessException(ErrorCode.FINAL_REPORT_NOT_READY);
        int version = snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(value -> value.getReportVersion() + 1).orElse(1);
        ObjectNode input = mapper.createObjectNode();
        input.put("contract", "final-business-proposal-input-v1"); input.put("projectId", projectId);
        input.put("version", version); input.put("sourceManifestHash", current.hash());
        input.set("sourceManifest", current.manifest().path("sources").deepCopy());
        ArrayNode included = input.putArray("includedSourceTypes");
        current.sources().forEach(source -> included.add(source.type()));
        ArrayNode omitted = input.putArray("omittedSourceTypes"); current.omitted().forEach(omitted::add);
        ObjectNode sourceData = input.putObject("sources");
        current.sources().forEach(source -> sourceData.set(source.type(), source.data().deepCopy()));
        String inputJson = write(input);
        String inputHash = inputHasher.hash(TaskType.FINAL_BUSINESS_PROPOSAL_GENERATION,
            "1.0", "ko-KR", inputJson);
        String reportId = current.hash().substring("sha256:".length());
        var created = taskRunService.createWithDisposition(ownerId, projectId,
            TaskType.FINAL_BUSINESS_PROPOSAL_GENERATION, "FINAL_BUSINESS_PROPOSAL", reportId,
            inputJson, inputHash, key, correlationId == null || correlationId.isBlank() ? key : correlationId, 2);
        if (created.createdNew()) publish(projectId, created.taskRun().getId(), "QUEUED",
            "job.final-report.queued", JobEvent.Status.QUEUED, null);
        return new ProposalActionResponse(reportId, created.taskRun().getId(),
            created.taskRun().getState().name(), current.hash());
    }

    @Transactional
    public String completeProposal(TaskRunService.Claim claim, TaskRunWorkerContext context,
            ExecutionResponse response) {
        JsonNode taskInput = json(context.inputSnapshot());
        JsonNode result = response.result().deepCopy();
        validateProposal(result);
        canonicalizeEvidence(result, taskInput.path("sourceManifest"));
        Project project = owned(context.ownerId(), context.projectId());
        List<String> selected = new ArrayList<>();
        taskInput.path("includedSourceTypes").forEach(item -> selected.add(item.asText()));
        SourceSet current = selectSources(sources(context.ownerId(), project), selected);
        if (!taskInput.path("sourceManifestHash").asText().equals(current.hash()))
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        String resultJson = write(result);
        taskRunService.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), resultJson,
            response.canonicalInputHash(), "1.0");
        int version = taskInput.path("version").asInt();
        FinalReportSnapshot saved = snapshots.findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(
            context.projectId(), context.idempotencyKey()).orElse(null);
        if (saved == null) {
            Binding binding = current.binding();
            saved = snapshots.save(FinalReportSnapshot.create(context.projectId(), version, write(current.manifest()),
                current.hash(), resultJson, Instant.now(), context.ownerId(), binding.marketSeedSnapshotId(),
                binding.selectionId(), binding.selectionRevision(), binding.bmPlanRevision(), current.bindingHash(),
                context.idempotencyKey(), context.inputHash(), MANIFEST_SCHEMA_VERSION));
        }
        return saved.getId();
    }

    @Transactional
    public void failProposal(TaskRunService.Claim claim, String code, String reason, boolean retryable) {
        taskRunService.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
    }

    @Transactional
    public ProposalActionResponse startReview(Long ownerId, Long projectId, String snapshotId,
            String idempotencyKey, String correlationId) {
        String key = requiredKey(idempotencyKey);
        owned(ownerId, projectId);
        FinalReportSnapshot snapshot = snapshots.findByIdAndProjectIdAndDeletedAtIsNull(snapshotId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ObjectNode input = mapper.createObjectNode();
        input.put("contract", "final-business-proposal-review-input-v1");
        input.put("projectId", projectId); input.put("snapshotId", snapshotId);
        input.put("sourceManifestHash", snapshot.getSourceManifestHash());
        input.set("sourceManifest", json(snapshot.getSourceManifestJson()).path("sources").deepCopy());
        input.set("proposal", json(snapshot.getReportJson()));
        String inputJson = write(input);
        String hash = inputHasher.hash(TaskType.FINAL_BUSINESS_PROPOSAL_REVIEW, "1.0", "ko-KR", inputJson);
        var created = taskRunService.createWithDisposition(ownerId, projectId,
            TaskType.FINAL_BUSINESS_PROPOSAL_REVIEW, "FINAL_BUSINESS_PROPOSAL_REVIEW", snapshotId,
            inputJson, hash, key, correlationId == null || correlationId.isBlank() ? key : correlationId, 2);
        if (created.createdNew()) publish(projectId, created.taskRun().getId(), "QUEUED",
            "job.final-report.review.queued", JobEvent.Status.QUEUED, null);
        return new ProposalActionResponse(snapshotId, created.taskRun().getId(),
            created.taskRun().getState().name(), snapshot.getSourceManifestHash());
    }

    @Transactional(readOnly = true)
    public ReviewView currentReview(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        String snapshotId = snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(FinalReportSnapshot::getId).orElse(null);
        return currentReview(ownerId, projectId, snapshotId);
    }

    @Transactional(readOnly = true)
    public ReviewView currentReview(Long ownerId, Long projectId, String snapshotId) {
        owned(ownerId, projectId);
        TaskRun run = taskRuns.findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, TaskType.FINAL_BUSINESS_PROPOSAL_REVIEW).orElse(null);
        if (run == null || snapshotId == null || !snapshotId.equals(run.getSubjectId()))
            return new ReviewView(null, "NOT_STARTED", null, null);
        var adopted = taskResults.findByTaskRunId(run.getId()).stream()
            .filter(result -> result.getValidationState() == TaskResultValidationState.ADOPTED).findFirst().orElse(null);
        return new ReviewView(run.getId(), run.getState().name(),
            adopted == null ? null : json(adopted.getResultJson()),
            adopted == null ? null : instant(adopted.getAdoptedAt()));
    }

    @Transactional
    public void completeReview(TaskRunService.Claim claim, TaskRunWorkerContext context,
            ExecutionResponse response) {
        JsonNode result = response.result().deepCopy();
        if (!result.isObject() || !"final-business-proposal-review-v1".equals(result.path("contract").asText()))
            throw new IllegalArgumentException("FINAL_BUSINESS_PROPOSAL_REVIEW_INVALID");
        canonicalizeEvidence(result, json(context.inputSnapshot()).path("sourceManifest"));
        taskRunService.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), write(result),
            response.canonicalInputHash(), "1.0");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Long projectId, String taskRunId, String stage, String key,
            JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(projectId, taskRunId, taskRunId, stage, key,
            status, key, java.util.Map.of(), code));
    }

    @Transactional
    public FinalReportView generate(Long ownerId, Long projectId, String idempotencyKey) {
        return generate(ownerId, projectId, idempotencyKey, OPTIONAL);
    }

    @Transactional
    public FinalReportView generate(Long ownerId, Long projectId, String idempotencyKey,
            List<String> includedOptionalSources) {
        String key = requiredKey(idempotencyKey);
        Project project = owned(ownerId, projectId);
        SourceSet allCurrent = sources(ownerId, project);
        SourceSet current = selectSources(allCurrent, includedOptionalSources);
        int nextVersion = snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(value -> value.getReportVersion() + 1).orElse(1);
        if (!current.ready()) return draft(project, current, nextVersion);

        String identityHash = commandIdentity(projectId, current);
        FinalReportSnapshot replay = snapshots
            .findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(projectId, key).orElse(null);
        if (replay != null) {
            if (!identityHash.equals(replay.getCommandIdentityHash()))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return view(exact(replay, current) ? State.CURRENT : State.STALE, replay, current);
        }

        Instant now = Instant.now();
        ObjectNode report = composer.compose(project, nextVersion, now, current.sources());
        SourceSet beforeSave = selectSources(sources(ownerId, project), includedOptionalSources);
        if (!beforeSave.ready() || !current.hash().equals(beforeSave.hash())
                || !current.bindingHash().equals(beforeSave.bindingHash())) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "분석 결과가 변경되었습니다. 최신 자료로 다시 생성해 주세요.");
        }
        Binding binding = current.binding();
        FinalReportSnapshot saved = snapshots.save(FinalReportSnapshot.create(projectId, nextVersion,
            write(current.manifest()), current.hash(), write(report), now, ownerId,
            binding.marketSeedSnapshotId(), binding.selectionId(), binding.selectionRevision(),
            binding.bmPlanRevision(), current.bindingHash(), key, identityHash, MANIFEST_SCHEMA_VERSION));
        return view(State.CURRENT, saved, current);
    }

    private SourceSet selectSources(SourceSet source, List<String> includedOptionalSources) {
        java.util.Set<String> selectedOptional = includedOptionalSources == null ? new java.util.HashSet<>()
            : includedOptionalSources.stream().filter(OPTIONAL::contains)
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
        if (selectedOptional.contains("MARKETING")) selectedOptional.add("MARKETING_ASSETS");
        if (selectedOptional.contains("FINANCE")) selectedOptional.add("FINANCE_REPORT");
        List<ReportSource> selected = source.sources().stream()
            .filter(item -> !OPTIONAL.contains(item.type()) || selectedOptional.contains(item.type()))
            .toList();
        java.util.Set<String> includedTypes = selected.stream().map(ReportSource::type)
            .collect(java.util.stream.Collectors.toSet());
        List<String> omitted = OPTIONAL.stream().filter(type -> !includedTypes.contains(type)).toList();
        ObjectNode manifest = composer.manifest(source.binding() == null ? null : bindingJson(source.binding()), selected);
        ArrayNode omittedValues = manifest.putArray("omittedSources");
        omitted.forEach(omittedValues::add);
        return new SourceSet(selected, manifest, composer.hash(manifest), source.binding(), source.bindingHash(),
            source.readiness(), source.blocking(), omitted);
    }

    /** HTTP generation requires an explicit Idempotency-Key. */
    public FinalReportView generate(Long ownerId, Long projectId) {
        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
    }

    private FinalReportView view(State state, FinalReportSnapshot snapshot, SourceSet current) {
        String generatedByName = users.findByIdAndDeletedAtIsNull(snapshot.getGeneratedBy())
            .map(user -> user.getName()).orElse("알 수 없는 사용자");
        return new FinalReportView(state, snapshot.getId(), snapshot.getReportVersion(), snapshot.getGeneratedAt(),
            snapshot.getSourceManifestHash(), json(snapshot.getSourceManifestJson()), json(snapshot.getReportJson()),
            snapshot.getGeneratedBy(), generatedByName,
            current.readiness(), combined(current), current.blocking(), current.omitted());
    }

    private FinalReportView draft(Project project, SourceSet current, int version) {
        Instant now = Instant.now();
        return new FinalReportView(current.ready() ? State.READY : State.NOT_READY, null, null, null,
            current.hash(), current.manifest(),
            composer.compose(project, version, now, current.sources()), null, null,
            current.readiness(), combined(current),
            current.blocking(), current.omitted());
    }

    private List<String> combined(SourceSet source) {
        List<String> result = new ArrayList<>(source.blocking()); result.addAll(source.omitted());
        return List.copyOf(result);
    }

    private SourceSet sources(Long ownerId, Project project) {
        Long projectId = project.getId();
        List<ReportSource> values = new ArrayList<>();
        ObjectNode projectData = mapper.createObjectNode();
        projectData.put("name", project.getTitle()); putNullable(projectData, "description", project.getDescription());
        putNullable(projectData, "industryCategory", project.getIndustryCategory());
        values.add(source("PROJECT", String.valueOf(projectId), project.getVersion().intValue(), null,
            null, instant(project.getUpdatedAt()), projectData));

        Source authority = currentSource(projectId);
        List<String> blocking = new ArrayList<>();
        if (authority == null) {
            blocking.add("CURRENT_CONCEPT");
            return sourceSet(values, null, blocking, OPTIONAL, ownerId, projectId);
        }
        Binding binding = currentConcepts.binding(authority);
        ObjectNode bindingJson = bindingJson(binding);
        ObjectNode conceptData = mapper.createObjectNode();
        conceptData.set("concept", json(authority.seed().getSnapshotJson()));
        conceptData.set("businessModel", authority.bm().plan().deepCopy());
        conceptData.set("constraints", authority.bm().constraints().deepCopy());
        values.add(source("CURRENT_CONCEPT", authority.seed().getId(), null, binding.selectionRevision(),
            authority.seed().getSnapshotHash(), authority.seed().getFinalizedAt(), conceptData));

        BusinessValidationSession session = validationSessions
            .findFirstByProjectIdAndSourceMarketSeedSnapshotIdAndSourcePortfolioSelectionIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndStateAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, binding.marketSeedSnapshotId(), binding.selectionId(), binding.selectionRevision(),
                binding.bmPlanRevision(), BusinessValidationSession.State.COMPLETED).orElse(null);
        if (session == null || session.getMarketVersionId() == null || session.getBmVersionId() == null) {
            blocking.add("BUSINESS_VALIDATION");
        } else {
            ObjectNode sessionData = mapper.createObjectNode(); sessionData.put("state", "COMPLETED");
            values.add(source("BUSINESS_VALIDATION_SESSION", session.getId(), null, null,
                composer.hash(sessionData), instant(session.getUpdatedAt()), sessionData));
            marketVersions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(session.getMarketVersionId(), projectId,
                MarketResearchRun.Kind.FULL).ifPresent(version -> values.add(source("MARKET", String.valueOf(version.getId()),
                    version.getVersionNumber(), null, null, instant(version.getUpdatedAt()), json(version.getResultJson()))));
            marketVersions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(session.getBmVersionId(), projectId,
                MarketResearchRun.Kind.BM).ifPresent(version -> values.add(source("BUSINESS_MODEL", String.valueOf(version.getId()),
                    version.getVersionNumber(), null, null, instant(version.getUpdatedAt()), json(version.getResultJson()))));
            if (!has(values, "MARKET")) blocking.add("MARKET");
            if (!has(values, "BUSINESS_MODEL")) blocking.add("BUSINESS_MODEL");
        }

        addMarketInterview(values, projectId, binding);
        addMarketing(values, projectId, binding);
        addLaunch(values, projectId, ModuleType.TECHNOLOGY, "LAUNCH_TECHNOLOGY");
        addLaunch(values, projectId, ModuleType.OPERATIONS, "LAUNCH_OPERATIONS");
        addFinance(values, projectId, binding);
        addMarketingStrategy(values, projectId);
        List<String> omitted = OPTIONAL.stream().filter(type -> !has(values, type)).toList();
        return sourceSet(values, bindingJson, blocking, omitted, ownerId, projectId);
    }

    private void addMarketInterview(List<ReportSource> values, Long projectId, Binding binding) {
        marketInterviews.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .filter(run -> run.getState() == MarketInterviewRun.State.SUCCEEDED
                && run.getSourceMarketSeedSnapshotId().equals(binding.marketSeedSnapshotId())
                && run.getSourceSelectionId().equals(binding.selectionId())
                && run.getSourceSelectionRevision() == binding.selectionRevision()
                && run.getSourceBmPlanRevision() == binding.bmPlanRevision())
            .ifPresent(run -> values.add(source("MARKET_INTERVIEW", String.valueOf(run.getId()), null,
                run.getAttempt(), run.getInputHash(), instant(run.getCompletedAt()), json(run.getResultJson()))));
    }

    private void addMarketing(List<ReportSource> values, Long projectId, Binding binding) {
        marketingSources
            .findBySourceMarketSeedSnapshotIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndProjectIdAndDeletedAtIsNull(
                binding.marketSeedSnapshotId(), binding.selectionRevision(), binding.bmPlanRevision(), projectId)
            .filter(source -> Objects.equals(source.getPortfolioSelectionId(), binding.selectionId()))
            .map(source -> marketingContents
                .findFirstByProjectIdAndMarketingSourceSnapshotIdAndStatusAndDeletedAtIsNullOrderByFinalizedAtDesc(
                    projectId, source.getId(), MarketingContentStatus.FINALIZED)
                .orElseGet(() -> marketingContents
                    .findFirstByProjectIdAndMarketingSourceSnapshotIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                        projectId, source.getId(), MarketingContentStatus.COMPLETED).orElse(null)))
            .filter(Objects::nonNull)
            .ifPresent(content -> {
                boolean draft = content.getStatus() == MarketingContentStatus.COMPLETED;
                int revisionNumber = draft ? content.getCurrentRevisionNumber() : content.getFinalizedRevisionNumber();
                marketingRevisions.findByContentIdAndRevisionNumberAndDeletedAtIsNull(content.getId(), revisionNumber)
                .ifPresent(revision -> {
                    JsonNode raw = json(revision.getResultJson());
                    ObjectNode data = raw.isObject() ? (ObjectNode) raw.deepCopy() : mapper.createObjectNode().set("result", raw);
                    data.putObject("_sourceMetadata").put("draft", draft).put("status", content.getStatus().name());
                    values.add(source("MARKETING", revision.getId(), null, revision.getRevisionNumber(), null,
                        draft ? instant(content.getUpdatedAt()) : content.getFinalizedAt(), data));
                    ArrayNode assets = mapper.createArrayNode();
                    marketingAssets.findAllByContentIdAndDeletedAtIsNullOrderByCreatedAtAsc(content.getId())
                        .forEach(asset -> assets.addObject().put("artifactRef", asset.getArtifactRef()));
                    if (!assets.isEmpty()) values.add(source("MARKETING_ASSETS", content.getId(), null,
                        revision.getRevisionNumber(), null, content.getFinalizedAt(), assets));
                });
            });
    }

    private void addMarketingStrategy(List<ReportSource> values, Long projectId) {
        String currentHash = strategySourceHash(values);
        marketingStrategies.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .filter(report -> currentHash.equals(report.getSourceManifestHash()))
            .ifPresent(report -> values.add(source("MARKETING_STRATEGY", report.getId(), null, null,
                composer.hash(json(report.getResultJson())), report.getGeneratedAt(), json(report.getResultJson()))));
    }

    private String strategySourceHash(List<ReportSource> values) {
        List<ReportSource> context = values.stream()
            .filter(item -> "PROJECT".equals(item.type()) || STRATEGY_CONTEXT_TYPES.contains(item.type()))
            .toList();
        return composer.hash(composer.manifest(context));
    }

    private void addLaunch(List<ReportSource> values, Long projectId, ModuleType type, String sourceType) {
        LaunchReadinessInputSnapshot input = launchInputs
            .findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, type)
            .filter(value -> !value.isStale()).orElse(null);
        if (input == null) return;
        launchReports.findFirstByProjectIdAndModuleTypeAndInputSnapshotIdAndDeletedAtIsNullOrderByCompletedAtDesc(
                projectId, type, input.getId()).filter(report -> report.isCurrent() && !report.isStale())
            .ifPresent(report -> {
                ObjectNode data = mapper.createObjectNode(); data.set("analysis", json(report.getAnalysisJson()));
                data.set("quality", json(report.getQualityJson()));
                data.set("externalEvidence", json(report.getExternalEvidenceJson()));
                values.add(source(sourceType, report.getId(), null, input.getAttempt(), report.getResultHash(),
                    report.getCompletedAt(), data));
            });
    }

    private void addFinance(List<ReportSource> values, Long projectId, Binding binding) {
        var snapshot = financeSnapshots
            .findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, "USER_DOCUMENT_INPUT")
            .orElseGet(() -> financeSnapshots
                .findFirstByProjectIdAndSourceCurrentMarketSeedSnapshotIdAndSourceSelectionIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndDeletedAtIsNullOrderByFinalizedAtDesc(
                    projectId, binding.marketSeedSnapshotId(), binding.selectionId(), binding.selectionRevision(), binding.bmPlanRevision())
                .orElse(null));
        if (snapshot == null) return;
        String subjectId = "USER_DOCUMENT_INPUT".equals(snapshot.getSourceMode())
            ? "USER_DOCUMENT_INPUT" : snapshot.getId();
        taskRuns.findByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, "FINANCIAL_ANALYSIS_REPORT", subjectId).stream()
            .filter(run -> financeSnapshotMatches(run, snapshot.getId())).findFirst()
            .flatMap(run -> taskResults.findByTaskRunId(run.getId()).stream()
                .filter(result -> result.getValidationState() == TaskResultValidationState.ADOPTED).findFirst())
            .ifPresent(result -> {
                values.add(source("FINANCE", snapshot.getId(), null, null, snapshot.getSnapshotHash(),
                    snapshot.getFinalizedAt(), json(snapshot.getSnapshotJson())));
                values.add(source("FINANCE_REPORT", result.getId(), null, null, result.getResultHash(),
                    instant(result.getAdoptedAt()), json(result.getResultJson())));
            });
    }

    private boolean financeSnapshotMatches(TaskRun run, String snapshotId) {
        JsonNode input = json(run.getInputSnapshot());
        String referenced = input.path("snapshotId").asText();
        if (referenced.isBlank()) referenced = input.path("inputSnapshot").path("snapshotId").asText();
        return snapshotId.equals(referenced);
    }

    private java.util.Map<String, String> sourceStates(SourceSet current, Long projectId) {
        java.util.Map<String, String> states = new java.util.LinkedHashMap<>();
        for (String type : List.of("MARKET_INTERVIEW", "MARKETING_STRATEGY", "MARKETING",
                "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE")) states.put(type, "NOT_RUN");
        current.sources().forEach(source -> {
            if (!states.containsKey(source.type())) return;
            if ("MARKETING".equals(source.type()) && source.data().path("_sourceMetadata").path("draft").asBoolean())
                states.put(source.type(), "AVAILABLE_DRAFT");
            else if ("MARKETING".equals(source.type())) states.put(source.type(), "AVAILABLE_FINAL");
            else states.put(source.type(), "AVAILABLE");
        });
        if (!has(current.sources(), "MARKET_INTERVIEW")) marketInterviews
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .ifPresent(run -> states.put("MARKET_INTERVIEW", switch (run.getState()) {
                case FAILED -> "FAILED"; case RUNNING -> "IN_PROGRESS"; default -> "CURRENT_RESULT_UNAVAILABLE";
            }));
        if (!has(current.sources(), "MARKETING")) marketingContents
            .findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
            .ifPresent(content -> states.put("MARKETING", switch (content.getStatus()) {
                case FAILED -> "FAILED"; case QUEUED, RUNNING -> "IN_PROGRESS";
                case COMPLETED -> "CURRENT_RESULT_UNAVAILABLE"; default -> "CURRENT_RESULT_UNAVAILABLE";
            }));
        if (!has(current.sources(), "MARKETING_STRATEGY")) {
            var latestReport = marketingStrategies
                .findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
            var latestTask = taskRuns
                .findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                    projectId, TaskType.MARKETING_STRATEGY_GENERATION).orElse(null);
            if (latestTask != null && java.util.Set.of("QUEUED", "CLAIMED", "RUNNING", "READY")
                    .contains(latestTask.getState().name())) states.put("MARKETING_STRATEGY", "IN_PROGRESS");
            else if (latestTask != null && "FAILED".equals(latestTask.getState().name()))
                states.put("MARKETING_STRATEGY", latestReport == null ? "FAILED" : "UPDATE_REQUIRED");
            else if (latestReport != null) states.put("MARKETING_STRATEGY", "UPDATE_REQUIRED");
        }
        if (!has(current.sources(), "FINANCE") && financeSnapshots
            .findFirstByProjectIdAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId).isPresent())
            states.put("FINANCE", "CURRENT_RESULT_UNAVAILABLE");
        return java.util.Map.copyOf(states);
    }

    private SourceSet sourceSet(List<ReportSource> sources, JsonNode bindingJson, List<String> blocking,
            List<String> omitted, Long ownerId, Long projectId) {
        ObjectNode manifest = composer.manifest(bindingJson, sources);
        ArrayNode omittedValues = manifest.putArray("omittedSources");
        omitted.forEach(omittedValues::add);
        Binding binding = bindingJson == null ? null : new Binding(bindingJson.path("marketSeedSnapshotId").asText(),
            bindingJson.path("selectionId").asLong(), bindingJson.path("selectionRevision").asInt(),
            bindingJson.path("bmPlanRevision").asInt());
        String bindingHash = bindingJson == null ? null : composer.hash(bindingJson);
        return new SourceSet(List.copyOf(sources), manifest, composer.hash(manifest), binding, bindingHash,
            readiness(moduleStatuses.findAll(ownerId, projectId)), List.copyOf(blocking), List.copyOf(omitted));
    }

    public record CurrentSourceCatalog(ArrayNode manifest, ObjectNode sources,
                                       java.util.Map<String, String> sourceStates,
                                       List<String> blockingSources, List<String> omittedSources,
                                       String hash, String strategySourceHash) {}

    private boolean exact(FinalReportSnapshot snapshot, SourceSet current) {
        if (!snapshot.hasExactLineage() || current.binding() == null) return false;
        if (!"final-business-proposal-result-v1".equals(json(snapshot.getReportJson()).path("contract").asText()))
            return false;
        JsonNode savedManifest = json(snapshot.getSourceManifestJson());
        List<String> selectedOptional = new ArrayList<>();
        savedManifest.path("sources").forEach(item -> {
            String type = item.path("type").asText();
            if (OPTIONAL.contains(type)) selectedOptional.add(type);
        });
        SourceSet comparable = selectSources(current, selectedOptional);
        Binding binding = current.binding();
        return snapshot.getSourceManifestHash().equals(comparable.hash())
            && snapshot.getSourceBindingHash().equals(current.bindingHash())
            && snapshot.getSourceMarketSeedSnapshotId().equals(binding.marketSeedSnapshotId())
            && snapshot.getSourceSelectionId().equals(binding.selectionId())
            && snapshot.getSourceSelectionRevision() == binding.selectionRevision()
            && snapshot.getSourceBmPlanRevision() == binding.bmPlanRevision();
    }

    private boolean bound(LaunchReadinessInputSnapshot value, Binding binding) {
        return Objects.equals(value.getSourceMarketSeedSnapshotId(), binding.marketSeedSnapshotId())
            && Objects.equals(value.getSourceSelectionId(), binding.selectionId())
            && Objects.equals(value.getSourceSelectionRevision(), binding.selectionRevision())
            && Objects.equals(value.getSourceBmPlanRevision(), binding.bmPlanRevision());
    }

    private ObjectNode bindingJson(Binding binding) {
        ObjectNode value = mapper.createObjectNode(); value.put("marketSeedSnapshotId", binding.marketSeedSnapshotId());
        value.put("selectionId", binding.selectionId()); value.put("selectionRevision", binding.selectionRevision());
        value.put("bmPlanRevision", binding.bmPlanRevision()); return value;
    }

    private String commandIdentity(Long projectId, SourceSet sources) {
        ObjectNode value = mapper.createObjectNode(); value.put("projectId", projectId);
        value.put("sourceBindingHash", sources.bindingHash()); value.put("sourceManifestHash", sources.hash());
        return composer.hash(value);
    }

    private void validateProposal(JsonNode result) {
        if (!result.isObject()
                || !"final-business-proposal-result-v1".equals(result.path("contract").asText())
                || !result.path("cover").isObject()
                || !result.path("executiveDecisionSummary").isObject()
                || !result.path("decisionRequest").isObject()
                || !result.path("appendix").isObject()
                || !result.path("sections").isArray()
                || result.path("sections").size() < 8
                || result.path("sections").size() > 10) {
            throw new IllegalArgumentException("FINAL_BUSINESS_PROPOSAL_RESULT_INVALID");
        }
    }

    private void canonicalizeEvidence(JsonNode result, JsonNode manifest) {
        java.util.Map<String, String> byType = new java.util.HashMap<>();
        java.util.Set<String> duplicateTypes = new java.util.HashSet<>();
        manifest.forEach(item -> {
            String type = item.path("type").asText();
            String reference = type + ":" + item.path("id").asText();
            if (byType.putIfAbsent(type, reference) != null) duplicateTypes.add(type);
        });
        duplicateTypes.forEach(byType::remove);
        canonicalizeEvidenceNode(result, byType);
    }

    private void canonicalizeEvidenceNode(JsonNode node, java.util.Map<String, String> byType) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            if (object.path("evidenceSourceTypes").isArray()) {
                ArrayNode refs = mapper.createArrayNode();
                java.util.Set<String> seen = new java.util.LinkedHashSet<>();
                object.path("evidenceSourceTypes").forEach(typeNode -> {
                    String reference = byType.get(typeNode.asText());
                    if (reference == null) throw new IllegalArgumentException("FINAL_REPORT_EVIDENCE_INVALID");
                    if (seen.add(reference)) refs.add(reference);
                });
                object.set("evidenceRefs", refs);
            }
            java.util.List<JsonNode> children = new ArrayList<>();
            object.forEach(children::add);
            children.forEach(child -> canonicalizeEvidenceNode(child, byType));
        } else if (node.isArray()) {
            node.forEach(child -> canonicalizeEvidenceNode(child, byType));
        }
    }

    private Source currentSource(Long projectId) {
        try { return currentConcepts.currentOrNull(projectId); }
        catch (BusinessException unavailable) { return null; }
    }

    private List<ReadinessItem> readiness(List<ProjectModuleStatusResponse> statuses) {
        return statuses.stream().map(value -> new ReadinessItem(value.module().name(), value.module().name(),
            value.status() == null ? "NOT_STARTED" : value.status().name())).toList();
    }

    private boolean has(List<ReportSource> sources, String type) {
        return sources.stream().anyMatch(value -> value.type().equals(type));
    }

    private ReportSource source(String type, String id, Integer version, Integer revision,
            String hash, Instant generatedAt, JsonNode data) {
        return new ReportSource(type, id, version, revision, hash == null ? composer.hash(data) : hash, generatedAt, data);
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private String requiredKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128)
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        return value.strip();
    }

    private JsonNode json(String value) {
        try { return value == null || value.isBlank() ? mapper.nullNode() : mapper.readTree(value); }
        catch (Exception ignored) { return mapper.getNodeFactory().textNode(value); }
    }

    private String write(JsonNode value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("최종 보고서 JSON을 저장할 수 없습니다.", error); }
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }

    private record SourceSet(List<ReportSource> sources, ObjectNode manifest, String hash,
        Binding binding, String bindingHash, List<ReadinessItem> readiness,
        List<String> blocking, List<String> omitted) {
        boolean ready() { return blocking.isEmpty() && binding != null; }
    }
}
