package com.aivle.backend.pipeline.finalreport.application;

import static com.aivle.backend.pipeline.finalreport.api.FinalReportApiModels.*;
import static com.aivle.backend.pipeline.finalreport.application.FinalReportComposer.ReportSource;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.finalreport.domain.FinalReportSnapshot;
import com.aivle.backend.pipeline.finalreport.repository.FinalReportSnapshotRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import com.aivle.backend.pipeline.launchreadiness.repository.LaunchReadinessReportRepository;
import com.aivle.backend.pipeline.marketing.domain.MarketingContentStatus;
import com.aivle.backend.pipeline.marketing.repository.MarketingAssetRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRevisionRepository;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.market.TwinSurveyVersionRepository;
import com.aivle.backend.pipeline.module.PipelineModuleStatus;
import com.aivle.backend.pipeline.module.PipelineModuleType;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse;
import com.aivle.backend.pipeline.module.ProjectModuleStatusService;
import com.aivle.backend.pipeline.techops.repository.TechOpsAdvisoryReportRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskResultValidationState;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinalReportService {
    private static final List<String> REQUIRED = List.of(
        "IDEA", "SELECTED_CONCEPT", "MARKET", "BUSINESS_MODEL",
        "FINANCE", "TWIN_SURVEY", "MARKETING");

    private final ProjectRepository projects;
    private final IdeaBriefRepository ideaBriefs;
    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioConceptRepository concepts;
    private final MarketResearchVersionRepository marketVersions;
    private final TechOpsAdvisoryReportRepository techOpsReports;
    private final LaunchReadinessReportRepository launchReadinessReports;
    private final FinancialInputSnapshotRepository financeSnapshots;
    private final TwinSurveyVersionRepository twinVersions;
    private final MarketingContentRepository marketingContents;
    private final MarketingContentRevisionRepository marketingRevisions;
    private final MarketingAssetRepository marketingAssets;
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
        State state = snapshot.getSourceManifestHash().equals(current.hash()) && current.ready()
            ? State.CURRENT : State.STALE;
        return new FinalReportView(state, snapshot.getId(), snapshot.getReportVersion(), snapshot.getGeneratedAt(),
            snapshot.getSourceManifestHash(), json(snapshot.getSourceManifestJson()), json(snapshot.getReportJson()),
            current.readiness(), current.missing());
    }

    public State state(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        return snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(snapshot -> snapshot.getSourceManifestHash().equals(current.hash()) && current.ready()
                ? State.CURRENT : State.STALE)
            .orElse(State.NOT_READY);
    }

    @Transactional
    public FinalReportView generate(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        int version = snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(value -> value.getReportVersion() + 1).orElse(1);
        if (!current.ready()) return draft(project, current, version);
        Instant now = Instant.now();
        ObjectNode report = composer.compose(project, version, now, current.sources());
        FinalReportSnapshot saved = snapshots.save(FinalReportSnapshot.create(projectId, version,
            write(current.manifest()), current.hash(), write(report), now, ownerId));
        return new FinalReportView(State.CURRENT, saved.getId(), version, now, current.hash(),
            current.manifest(), report, current.readiness(), List.of());
    }

    private FinalReportView draft(Project project, SourceSet current, int version) {
        Instant now = Instant.now();
        return new FinalReportView(State.NOT_READY, null, null, null, current.hash(), current.manifest(),
            composer.compose(project, version, now, current.sources()), current.readiness(), current.missing());
    }

    private SourceSet sources(Long ownerId, Project project) {
        Long projectId = project.getId();
        List<ReportSource> sources = new ArrayList<>();
        ObjectNode projectData = mapper.createObjectNode();
        projectData.put("name", project.getTitle());
        putNullable(projectData, "description", project.getDescription());
        putNullable(projectData, "industryCategory", project.getIndustryCategory());
        projectData.put("status", project.getStatus().name());
        sources.add(source("PROJECT", String.valueOf(projectId), project.getVersion().intValue(), null,
            null, instant(project.getUpdatedAt()), projectData));

        var currentBrief = ideaBriefs.findCurrentOwned(ownerId, projectId).orElse(null);
        if (currentBrief != null && currentBrief.getConfirmedSnapshotId() != null) {
            ideaBriefs.findByIdAndProjectIdAndDeletedAtIsNull(currentBrief.getConfirmedSnapshotId(), projectId)
                .ifPresent(brief -> {
                    ObjectNode data = mapper.createObjectNode();
                    putNullable(data, "overview", brief.getOverviewText());
                    putNullable(data, "summary", brief.getUserFacingSummary());
                    data.set("interpretation", json(brief.getInterpretationJson()));
                    sources.add(source("IDEA", brief.getId(), Math.toIntExact(brief.getBriefSequence()), null,
                        brief.getSnapshotHash(), instant(brief.getUpdatedAt()), data));
                });
        }

        var selection = selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        if (selection != null) {
            concepts.findByIdAndProjectIdAndDeletedAtIsNull(selection.getConceptId(), projectId).ifPresent(concept -> {
                ObjectNode data = mapper.createObjectNode();
                data.put("name", concept.getConceptName());
                data.put("summary", concept.getSummary());
                data.set("candidate", json(concept.getCandidateSnapshotJson()));
                sources.add(source("SELECTED_CONCEPT", concept.getId(), null, selection.getHypothesisRevision(),
                    concept.getCanonicalHash(), selection.getSelectedAt(), data));
                sources.add(source("LEGAL", concept.getId(), null, selection.getHypothesisRevision(),
                    selection.getBaseLegalHash(), selection.getSelectedAt(), json(concept.getLegalReviewJson())));
            });
        }

        marketVersions.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(projectId, MarketResearchRun.Kind.FULL)
            .ifPresent(value -> sources.add(source("MARKET", String.valueOf(value.getId()), value.getVersionNumber(), null,
                null, instant(value.getUpdatedAt()), json(value.getResultJson()))));
        marketVersions.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(projectId, MarketResearchRun.Kind.BM)
            .ifPresent(value -> sources.add(source("BUSINESS_MODEL", String.valueOf(value.getId()), value.getVersionNumber(), null,
                null, instant(value.getUpdatedAt()), json(value.getResultJson()))));
        techOpsReports.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .ifPresent(value -> sources.add(source("TECH_OPS", value.getId(), null, null, null,
                instant(value.getUpdatedAt()), json(value.getResultJson()))));
        addLaunchReadinessSource(sources, projectId, ModuleType.TECHNOLOGY, "LAUNCH_TECHNOLOGY");
        addLaunchReadinessSource(sources, projectId, ModuleType.OPERATIONS, "LAUNCH_OPERATIONS");
        financeSnapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId).ifPresent(value -> {
            sources.add(source("FINANCE", value.getId(), null, null, value.getSnapshotHash(),
                value.getFinalizedAt(), json(value.getSnapshotJson())));
            taskRuns.findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, "FINANCIAL_ANALYSIS_REPORT", value.getId()).ifPresent(run ->
                    taskResults.findByTaskRunId(run.getId()).stream()
                        .filter(result -> result.getValidationState() == TaskResultValidationState.ADOPTED)
                        .findFirst().ifPresent(result -> sources.add(source("FINANCE_REPORT", result.getId(), null, null,
                            result.getResultHash(), instant(result.getAdoptedAt()), json(result.getResultJson())))));
        });
        twinVersions.findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId)
            .ifPresent(value -> sources.add(source("TWIN_SURVEY", String.valueOf(value.getId()), value.getVersionNumber(), null,
                null, instant(value.getUpdatedAt()), json(value.getResultJson()))));

        marketingContents.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
            .filter(content -> content.getStatus() == MarketingContentStatus.FINALIZED).ifPresent(content ->
                marketingRevisions.findFirstByContentIdAndDeletedAtIsNullOrderByRevisionNumberDesc(content.getId())
                    .ifPresent(revision -> {
                        sources.add(source("MARKETING", revision.getId(), null, revision.getRevisionNumber(), null,
                            content.getFinalizedAt(), json(revision.getResultJson())));
                        ArrayNode assets = mapper.createArrayNode();
                        marketingAssets.findAllByContentIdAndDeletedAtIsNullOrderByCreatedAtAsc(content.getId())
                            .forEach(asset -> assets.addObject().put("artifactId", asset.getId()).put("artifactRef", asset.getArtifactRef()));
                        if (!assets.isEmpty()) sources.add(source("MARKETING_ASSETS", content.getId(), null,
                            revision.getRevisionNumber(), null, content.getFinalizedAt(), assets));
                    }));

        ArrayNode manifest = composer.manifest(sources);
        String hash = composer.hash(manifest);
        List<String> missing = new ArrayList<>(REQUIRED.stream()
            .filter(type -> sources.stream().noneMatch(source -> source.type().equals(type))).toList());
        boolean legacyLaunch = sources.stream().anyMatch(source -> source.type().equals("TECH_OPS"));
        boolean professionalLaunch = sources.stream().anyMatch(source -> source.type().equals("LAUNCH_TECHNOLOGY"))
            && sources.stream().anyMatch(source -> source.type().equals("LAUNCH_OPERATIONS"));
        if (!legacyLaunch && !professionalLaunch) missing.add("LAUNCH_READINESS");
        List<ReadinessItem> readiness = readiness(moduleStatuses.findAll(ownerId, projectId));
        boolean stagesComplete = readiness.stream().allMatch(item -> item.status().equals("COMPLETED"));
        return new SourceSet(List.copyOf(sources), manifest, hash, readiness, List.copyOf(missing), missing.isEmpty() && stagesComplete);
    }

    private void addLaunchReadinessSource(List<ReportSource> sources, Long projectId,
            ModuleType moduleType, String sourceType) {
        launchReadinessReports
            .findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, moduleType)
            .ifPresent(value -> sources.add(source(sourceType, value.getId(), null, null,
                value.getResultHash(), value.getCompletedAt(), json(value.getAnalysisJson()))));
    }

    private List<ReadinessItem> readiness(List<ProjectModuleStatusResponse> statuses) {
        Map<String, List<PipelineModuleType>> groups = Map.of(
            "planning", List.of(PipelineModuleType.IDEA, PipelineModuleType.CONCEPT_PORTFOLIO),
            "validation", List.of(PipelineModuleType.MARKET_ANALYSIS, PipelineModuleType.BUSINESS_MODEL),
            "launch", List.of(PipelineModuleType.TECH_OPS, PipelineModuleType.FINANCE),
            "interview", List.of(PipelineModuleType.TWIN_SURVEY),
            "marketingStrategy", List.of(PipelineModuleType.MARKETING));
        Map<String, String> labels = Map.of("planning", "사업 기획", "validation", "사업 검증",
            "launch", "출시 준비", "interview", "가상 인터뷰", "marketingStrategy", "마케팅 전략");
        return List.of("planning", "validation", "launch", "interview", "marketingStrategy").stream()
            .map(id -> new ReadinessItem(id, labels.get(id), aggregate(statuses.stream()
                .filter(value -> groups.get(id).contains(value.module())).map(ProjectModuleStatusResponse::status).toList())))
            .toList();
    }

    private String aggregate(List<PipelineModuleStatus> statuses) {
        if (statuses.stream().anyMatch(value -> value == PipelineModuleStatus.NEEDS_INPUT)) return "NEEDS_INPUT";
        if (statuses.stream().anyMatch(value -> value == PipelineModuleStatus.FAILED)) return "ATTENTION";
        if (statuses.stream().anyMatch(value -> value == PipelineModuleStatus.STALE)) return "STALE";
        if (statuses.stream().allMatch(value -> value == PipelineModuleStatus.COMPLETED)) return "COMPLETED";
        if (statuses.stream().anyMatch(value -> value == PipelineModuleStatus.RUNNING || value == PipelineModuleStatus.QUEUED
                || value == PipelineModuleStatus.COMPLETED)) return "IN_PROGRESS";
        if (statuses.stream().anyMatch(value -> value == PipelineModuleStatus.READY)) return "READY";
        return "NOT_STARTED";
    }

    private ReportSource source(String type, String id, Integer version, Integer revision,
            String hash, Instant generatedAt, JsonNode data) {
        return new ReportSource(type, id, version, revision, hash == null ? composer.hash(data) : hash, generatedAt, data);
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
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

    private record SourceSet(List<ReportSource> sources, ArrayNode manifest, String hash,
                             List<ReadinessItem> readiness, List<String> missing, boolean ready) {}
}
