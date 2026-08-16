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
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.market.TwinSurveyVersionRepository;
import com.aivle.backend.pipeline.market.TwinSurveyRunRepository;
import com.aivle.backend.pipeline.marketinterview.MarketInterviewRun;
import com.aivle.backend.pipeline.marketinterview.MarketInterviewRunRepository;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse;
import com.aivle.backend.pipeline.module.ProjectModuleStatusService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskResultValidationState;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinalReportService {
    private static final int MANIFEST_SCHEMA_VERSION = 2;
    private static final List<String> OPTIONAL = List.of("MARKET_INTERVIEW", "TWIN_SURVEY", "MARKETING",
        "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE");

    private final ProjectRepository projects;
    private final CurrentConceptSourceResolver currentConcepts;
    private final BusinessValidationSessionRepository validationSessions;
    private final MarketResearchVersionRepository marketVersions;
    private final MarketInterviewRunRepository marketInterviews;
    private final TwinSurveyVersionRepository twinVersions;
    private final TwinSurveyRunRepository twinRuns;
    private final MarketingSourceSnapshotRepository marketingSources;
    private final MarketingContentRepository marketingContents;
    private final MarketingContentRevisionRepository marketingRevisions;
    private final MarketingAssetRepository marketingAssets;
    private final LaunchReadinessInputSnapshotRepository launchInputs;
    private final LaunchReadinessReportRepository launchReports;
    private final FinancialInputSnapshotRepository financeSnapshots;
    private final TaskRunRepository taskRuns;
    private final TaskResultRepository taskResults;
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

    public State state(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        return snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(snapshot -> current.ready() && exact(snapshot, current) ? State.CURRENT : State.STALE)
            .orElse(current.ready() ? State.READY : State.NOT_READY);
    }

    @Transactional
    public FinalReportView generate(Long ownerId, Long projectId, String idempotencyKey) {
        String key = requiredKey(idempotencyKey);
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
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
        SourceSet beforeSave = sources(ownerId, project);
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

    /** HTTP generation requires an explicit Idempotency-Key. */
    public FinalReportView generate(Long ownerId, Long projectId) {
        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
    }

    private FinalReportView view(State state, FinalReportSnapshot snapshot, SourceSet current) {
        return new FinalReportView(state, snapshot.getId(), snapshot.getReportVersion(), snapshot.getGeneratedAt(),
            snapshot.getSourceManifestHash(), json(snapshot.getSourceManifestJson()), json(snapshot.getReportJson()),
            current.readiness(), combined(current), current.blocking(), current.omitted());
    }

    private FinalReportView draft(Project project, SourceSet current, int version) {
        Instant now = Instant.now();
        return new FinalReportView(current.ready() ? State.READY : State.NOT_READY, null, null, null,
            current.hash(), current.manifest(),
            composer.compose(project, version, now, current.sources()), current.readiness(), combined(current),
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

        addMarketInterview(values, projectId, binding); addTwin(values, projectId, binding);
        addMarketing(values, projectId, binding);
        addLaunch(values, projectId, binding, ModuleType.TECHNOLOGY, "LAUNCH_TECHNOLOGY");
        addLaunch(values, projectId, binding, ModuleType.OPERATIONS, "LAUNCH_OPERATIONS");
        addFinance(values, projectId, binding);
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

    private void addTwin(List<ReportSource> values, Long projectId, Binding binding) {
        twinRuns.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .filter(run -> run.getState() == com.aivle.backend.pipeline.market.TwinSurveyRun.State.SUCCEEDED
                && run.getSourceMarketSeedSnapshotId().equals(binding.marketSeedSnapshotId())
                && run.getSourcePortfolioSelectionId().equals(binding.selectionId())
                && run.getSourceSelectionRevision() == binding.selectionRevision()
                && run.getSourceBmPlanRevision() == binding.bmPlanRevision())
            .flatMap(run -> twinVersions.findBySourceRunIdAndDeletedAtIsNull(run.getId()))
            .ifPresent(version -> values.add(source("TWIN_SURVEY", String.valueOf(version.getId()), version.getVersionNumber(),
                null, null, instant(version.getUpdatedAt()), json(version.getResultJson()))));
    }

    private void addMarketing(List<ReportSource> values, Long projectId, Binding binding) {
        marketingSources
            .findBySourceMarketSeedSnapshotIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndProjectIdAndDeletedAtIsNull(
                binding.marketSeedSnapshotId(), binding.selectionRevision(), binding.bmPlanRevision(), projectId)
            .filter(source -> Objects.equals(source.getPortfolioSelectionId(), binding.selectionId()))
            .flatMap(source -> marketingContents
                .findFirstByProjectIdAndMarketingSourceSnapshotIdAndStatusAndDeletedAtIsNullOrderByFinalizedAtDesc(
                    projectId, source.getId(), MarketingContentStatus.FINALIZED))
            .ifPresent(content -> marketingRevisions
                .findByContentIdAndRevisionNumberAndDeletedAtIsNull(content.getId(), content.getFinalizedRevisionNumber())
                .ifPresent(revision -> {
                    values.add(source("MARKETING", revision.getId(), null, revision.getRevisionNumber(), null,
                        content.getFinalizedAt(), json(revision.getResultJson())));
                    ArrayNode assets = mapper.createArrayNode();
                    marketingAssets.findAllByContentIdAndDeletedAtIsNullOrderByCreatedAtAsc(content.getId())
                        .forEach(asset -> assets.addObject().put("artifactRef", asset.getArtifactRef()));
                    if (!assets.isEmpty()) values.add(source("MARKETING_ASSETS", content.getId(), null,
                        revision.getRevisionNumber(), null, content.getFinalizedAt(), assets));
                }));
    }

    private void addLaunch(List<ReportSource> values, Long projectId, Binding binding,
            ModuleType type, String sourceType) {
        LaunchReadinessInputSnapshot input = launchInputs
            .findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, type)
            .filter(value -> !value.isStale() && bound(value, binding)).orElse(null);
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
        financeSnapshots
            .findFirstByProjectIdAndSourceCurrentMarketSeedSnapshotIdAndSourceSelectionIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndDeletedAtIsNullOrderByFinalizedAtDesc(
                projectId, binding.marketSeedSnapshotId(), binding.selectionId(), binding.selectionRevision(), binding.bmPlanRevision())
            .ifPresent(snapshot -> {
                values.add(source("FINANCE", snapshot.getId(), null, null, snapshot.getSnapshotHash(),
                    snapshot.getFinalizedAt(), json(snapshot.getSnapshotJson())));
                taskRuns.findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                    projectId, "FINANCIAL_ANALYSIS_REPORT", snapshot.getId()).ifPresent(run ->
                        taskResults.findByTaskRunId(run.getId()).stream()
                            .filter(result -> result.getValidationState() == TaskResultValidationState.ADOPTED)
                            .findFirst().ifPresent(result -> values.add(source("FINANCE_REPORT", result.getId(), null,
                                null, result.getResultHash(), instant(result.getAdoptedAt()), json(result.getResultJson())))));
            });
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

    private boolean exact(FinalReportSnapshot snapshot, SourceSet current) {
        if (!snapshot.hasExactLineage() || current.binding() == null) return false;
        Binding binding = current.binding();
        return snapshot.getSourceManifestHash().equals(current.hash())
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
