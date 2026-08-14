package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelectionStatus;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.market.ledger.MarketLedgerArtifactService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Market FULL과 BM을 Target TaskRun/Worker/materialization 규칙에 연결한다. */
@Service
public class MarketResearchService {
    private static final String SCHEMA_VERSION = "1.0";
    private final ProjectRepository projects;
    private final ConceptPortfolioSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository seeds;
    private final MarketResearchRunRepository runs;
    private final MarketResearchVersionRepository versions;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher hasher;
    private final MarketResearchInputFactory inputs;
    private final BmPlanPreparationService bmPlans;
    private final ResearchCompetitorSeedService competitorSeeds;
    private final JobEventPublisher events;
    private final MarketLedgerArtifactService ledgerArtifacts;
    private final ObjectMapper mapper;

    public MarketResearchService(ProjectRepository projects,
            ConceptPortfolioSelectionRepository selections,
            MarketAnalysisSeedSnapshotRepository seeds,
            MarketResearchRunRepository runs, MarketResearchVersionRepository versions,
            TaskRunService taskRuns, CanonicalInputHasher hasher,
            MarketResearchInputFactory inputs, BmPlanPreparationService bmPlans,
            ResearchCompetitorSeedService competitorSeeds,
            MarketLedgerArtifactService ledgerArtifacts,
            JobEventPublisher events, ObjectMapper mapper) {
        this.projects=projects; this.selections=selections; this.seeds=seeds;
        this.runs=runs; this.versions=versions; this.taskRuns=taskRuns; this.hasher=hasher;
        this.inputs=inputs; this.bmPlans=bmPlans; this.competitorSeeds=competitorSeeds;
        this.ledgerArtifacts=ledgerArtifacts;
        this.events=events; this.mapper=mapper;
    }

    @Transactional
    public RunView startRecollect(Long ownerId, Long projectId, Long sourceVersionId,
            String slots, String from, String slotsFrom, String asOf,
            String idempotencyKey, String correlationId) {
        Project project = owned(ownerId, projectId);
        MarketResearchVersion source = versions
            .findByIdAndProjectIdAndKindAndDeletedAtIsNull(
                sourceVersionId, projectId, MarketResearchRun.Kind.FULL)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "Market recollect source version is unavailable."));
        MarketResearchVersion current = versions
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, MarketResearchRun.Kind.FULL)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!current.getId().equals(source.getId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "Only the current Market FULL version can be recollected.");
        }
        ConceptPortfolioSelection selection = readySelection(projectId);
        MarketAnalysisSeedSnapshot seed = seeds
            .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId())
            .filter(value -> "CONCEPT_PORTFOLIO_V2".equals(value.getSourceType()))
            .orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
        if (!seed.getId().equals(source.getSourceRun().getSourceMarketSeedSnapshotId())
                || !selection.getId().equals(source.getSourceRun().getSourcePortfolioSelectionId())
                || !Objects.equals(selection.getHypothesisRevision(), source.getSourceRun().getSourceSelectionRevision())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "Current concept lineage differs from the recollect source.");
        }
        if (!(from == null || from.isBlank() || "a4".equals(from) || "extract".equals(from))
                || !(slotsFrom == null || slotsFrom.isBlank()
                    || "source".equals(slotsFrom) || "current".equals(slotsFrom))
                || (slots != null && !slots.isBlank()
                    && !slots.matches("[A-Za-z0-9._-]{1,64}(,[A-Za-z0-9._-]{1,64}){0,31}"))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid recollect options.");
        }
        final MarketLedgerArtifactService.SourceView artifact;
        try {
            artifact = ledgerArtifacts.committedForVersion(source.getId());
        } catch (IllegalArgumentException missing) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "The durable Market ledger required for recollect is unavailable.");
        }
        String input = inputs.recollect(seed, selection, validAsOf(asOf),
            competitorSeeds.conceptBlock(projectId), bmPlans.current(projectId).constraints(),
            source, artifact.artifactId(), artifact.manifestHash(), artifact.sourceRunId(),
            artifact.marketTaskRunId(), artifact.taskAttemptId(), artifact.canonicalInputHash(),
            artifact.conceptSnapshotHash(), artifact.asOf(),
            slots, from, slotsFrom);
        return start(ownerId, project, MarketResearchRun.Kind.FULL, null, input,
            selection.getConceptId(), idempotencyKey, correlationId, source.getId(), seed.getId(),
            selection.getId(), selection.getHypothesisRevision(), null);
    }

    @Transactional
    public RunView startFull(Long ownerId, Long projectId, String asOf,
                             String idempotencyKey, String correlationId) {
        Project project=owned(ownerId,projectId);
        ConceptPortfolioSelection selection=readySelection(projectId);
        MarketAnalysisSeedSnapshot seed=seeds
            .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId())
            .filter(value -> "CONCEPT_PORTFOLIO_V2".equals(value.getSourceType()))
            .orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE,
                "current Market Analysis Seed가 필요합니다."));
        String input=inputs.full(seed,selection,validAsOf(asOf),competitorSeeds.conceptBlock(projectId),
            bmPlans.current(projectId).constraints());
        return start(ownerId,project,MarketResearchRun.Kind.FULL,null,input,selection.getConceptId(),
            idempotencyKey,correlationId,null,seed.getId(),selection.getId(),
            selection.getHypothesisRevision(),null);
    }

    @Transactional
    public RunView startBm(Long ownerId, Long projectId, String idempotencyKey, String correlationId) {
        Project project=owned(ownerId,projectId);
        MarketResearchVersion source=versions
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId,MarketResearchRun.Kind.FULL)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "완료된 Market Research 결과가 필요합니다."));
        ConceptPortfolioSelection selection=readySelection(projectId);
        MarketAnalysisSeedSnapshot seed=seeds
            .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId())
            .filter(value -> "CONCEPT_PORTFOLIO_V2".equals(value.getSourceType()))
            .orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
        if (!seed.getId().equals(source.getSourceRun().getSourceMarketSeedSnapshotId())
                || !selection.getId().equals(source.getSourceRun().getSourcePortfolioSelectionId())
                || !Objects.equals(selection.getHypothesisRevision(), source.getSourceRun().getSourceSelectionRevision())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "Current Market source changed; rerun Market Research before Business Model.");
        }
        var plan=bmPlans.forExecution(projectId).orElseGet(() -> bmPlans.current(projectId));
        JsonNode fullInput=mapper.readTree(source.getSourceRun().getTaskRun().getInputSnapshot());
        String input=inputs.bm(source,fullInput,plan.plan(),plan.constraints(),plan.revision());
        String conceptId=fullInput.path("conceptId").asText();
        return start(ownerId,project,MarketResearchRun.Kind.BM,source.getSourceRun(),input,conceptId,
            idempotencyKey,correlationId,source.getId(),
            source.getSourceRun().getSourceMarketSeedSnapshotId(),
            source.getSourceRun().getSourcePortfolioSelectionId(),
            source.getSourceRun().getSourceSelectionRevision(),plan.revision());
    }

    private RunView start(Long ownerId, Project project, MarketResearchRun.Kind kind,
            MarketResearchRun sourceRun, String input, String conceptId,
            String idempotencyKey, String correlationId, Long sourceVersionId,
            String seedId, Long selectionId, Integer selectionRevision, Integer planRevision) {
        String hash=hasher.hash(TaskType.MARKET_RESEARCH,SCHEMA_VERSION,"ko-KR",input);
        var created=taskRuns.createWithDisposition(ownerId,project.getId(),TaskType.MARKET_RESEARCH,
            "MARKET_RESEARCH_"+kind.name(),conceptId,input,hash,idempotencyKey,correlationId,1);
        MarketResearchRun domain;
        if (created.createdNew()) {
            domain=runs.save(MarketResearchRun.create(project,kind,sourceRun,created.taskRun(),hash,
                sourceVersionId,seedId,selectionId,selectionRevision,planRevision));
            publish(project.getId(),created.taskRun().getId(),"QUEUED",
                kind==MarketResearchRun.Kind.FULL ? "job.market.research.queued" : "job.business-model.queued",
                JobEvent.Status.QUEUED,null);
        } else {
            domain=runs.findByTaskRunIdAndDeletedAtIsNull(created.taskRun().getId())
                .orElseThrow(() -> new IllegalStateException("TaskRun replay lineage missing"));
        }
        return runView(domain);
    }

    @Transactional(readOnly=true)
    public CurrentView current(Long ownerId, Long projectId, MarketResearchRun.Kind kind) {
        owned(ownerId,projectId);
        ConceptPortfolioSelection selection=selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        MarketAnalysisSeedSnapshot seed=selection==null?null:seeds
            .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId()).orElse(null);
        SourceView source=selection==null?null:new SourceView(selection.getConceptId(),
            seed==null?null:mapper.readTree(seed.getSnapshotJson()).path("selectedConcept")
                .path("identity").path("conceptName").asText(null),
            selection.getId(),selection.getHypothesisRevision(),seed==null?null:seed.getId());
        MarketResearchRun run=runs
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,kind)
            .orElse(null);
        if(run==null)return new CurrentView(null,null,source,false);
        MarketResearchVersion version=versions.findBySourceRunIdAndDeletedAtIsNull(run.getId()).orElse(null);
        boolean stale;
        if(kind==MarketResearchRun.Kind.FULL){
            stale=seed==null||!Objects.equals(seed.getId(),run.getSourceMarketSeedSnapshotId());
        }else{
            MarketResearchVersion currentMarket=versions
                .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
                    projectId,MarketResearchRun.Kind.FULL).orElse(null);
            stale=currentMarket==null||!Objects.equals(currentMarket.getId(),run.getSourceMarketVersionId())
                || seed==null||!Objects.equals(seed.getId(),currentMarket.getSourceRun().getSourceMarketSeedSnapshotId());
        }
        return new CurrentView(runView(run),version==null?null:versionView(version),source,stale);
    }

    @Transactional
    public void markRunning(String taskRunId) {
        runs.findByTaskRunIdAndDeletedAtIsNull(taskRunId).ifPresent(run->{run.running();runs.save(run);});
    }

    @Transactional
    public void complete(TaskRunService.Claim claim, ExecutionResponse response) {
        MarketResearchRun run=runs.findByTaskRunIdAndDeletedAtIsNull(claim.taskRunId())
            .orElseThrow(() -> new IllegalStateException("Market run missing"));
        if(versions.findBySourceRunIdAndDeletedAtIsNull(run.getId()).isPresent())return;
        taskRuns.adopt(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),
            mapper.writeValueAsString(response.result()),response.canonicalInputHash(),response.resultSchemaVersion());
        JsonNode result=response.result();
        int evidenceCount=result.path("evidence").size(),caveatCount=0;
        for(JsonNode item:result.path("evidence"))caveatCount+=item.path("caveats").size();
        for(JsonNode cell:result.path("canvas").path("cells"))caveatCount+=cell.path("caveats").size();
        Integer filled=null,partial=null,missing=null;
        if(result.path("scorecard").isArray()){
            filled=0;partial=0;missing=0;
            for(JsonNode item:result.path("scorecard"))switch(item.path("state").asText()){
                case "FILLED"->filled++; case "PARTIAL"->partial++; case "MISSING"->missing++; default->{}
            }
        }
        JsonNode bm=result.path("bm");
        var summary=new MarketResearchVersion.Summary(filled,partial,missing,
            bm.isObject()?bm.path("decision").asText(null):null,
            bm.isObject()?bm.path("confidence").asText(null):null,evidenceCount,caveatCount);
        int number=Math.toIntExact(versions.countByProjectIdAndKindAndDeletedAtIsNull(
            run.getProject().getId(),run.getKind())+1);
        MarketResearchVersion version = versions.save(
            MarketResearchVersion.of(run.getProject(),run,number,result.toString(),summary));
        if (run.getKind() == MarketResearchRun.Kind.FULL) {
            ledgerArtifacts.commit(claim.taskRunId(), claim.taskAttemptId(), version);
        }
        run.running();run.succeed();runs.save(run);
    }

    @Transactional
    public void materializeFailure(String taskRunId,String code) {
        ledgerArtifacts.discardStaged(taskRunId);
        runs.findByTaskRunIdAndDeletedAtIsNull(taskRunId).ifPresent(run->{
            if(!run.terminal()){run.fail(code);runs.save(run);}
        });
    }

    @Transactional(readOnly=true)
    public BmPlanPreparationService.PlanView currentPlan(Long ownerId,Long projectId){
        owned(ownerId,projectId);return bmPlans.current(projectId);
    }
    @Transactional
    public BmPlanPreparationService.PlanView savePlan(Long ownerId,Long projectId,JsonNode plan,JsonNode constraints){
        owned(ownerId,projectId);return bmPlans.save(projectId,ownerId,plan,constraints);
    }
    @Transactional(readOnly=true)
    public ResearchCompetitorSeedService.SeedsView currentCompetitorSeeds(Long ownerId,Long projectId){
        owned(ownerId,projectId);return competitorSeeds.current(projectId);
    }
    @Transactional
    public ResearchCompetitorSeedService.SeedsView saveCompetitorSeeds(Long ownerId,Long projectId,JsonNode payload){
        owned(ownerId,projectId);return competitorSeeds.replace(projectId,ownerId,payload);
    }

    private ConceptPortfolioSelection readySelection(Long projectId){
        ConceptPortfolioSelection value=selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(()->new BusinessException(ErrorCode.CONCEPT_SELECTION_REQUIRED));
        if(value.getStatus()!=ConceptPortfolioSelectionStatus.READY_FOR_MARKET||value.getActiveTaskRunId()!=null)
            throw new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE,
                "선택·가설·최종 법률·Market Seed 준비가 완료되지 않았습니다.");
        return value;
    }
    private Project owned(Long ownerId,Long projectId){
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId,ownerId)
            .orElseThrow(()->new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
    private static String validAsOf(String value){
        try{return value==null||value.isBlank()?LocalDate.now().toString():LocalDate.parse(value).toString();}
        catch(RuntimeException invalid){throw new BusinessException(ErrorCode.INVALID_REQUEST,"asOf는 YYYY-MM-DD 형식이어야 합니다.");}
    }
    private void publish(Long projectId,String taskRunId,String stage,String key,JobEvent.Status status,String code){
        events.publish(new JobEventPublisher.Command(projectId,taskRunId,taskRunId,stage,key,status,key,Map.of(),code));
    }
    private RunView runView(MarketResearchRun run){
        TaskRun task=run.getTaskRun();
        return new RunView(run.getId(),run.getKind().name(),run.getState().name(),task.getId(),
            task.getState().name(),run.getErrorCode(),task.isRetryable());
    }
    private VersionView versionView(MarketResearchVersion version){
        return new VersionView(version.getId(),version.getKind().name(),version.getVersionNumber(),
            mapper.readTree(version.getResultJson()),version.getEvidenceCount(),version.getCaveatCount(),
            version.getDecision(),version.getConfidence(),version.getFilledCount(),
            version.getPartialCount(),version.getMissingCount());
    }
    public record RunView(Long id,String kind,String state,String taskRunId,String taskState,
                          String errorCode,boolean retryable){}
    public record VersionView(Long id,String kind,Integer versionNumber,JsonNode result,
            Integer evidenceCount,Integer caveatCount,String decision,String confidence,
            Integer filledCount,Integer partialCount,Integer missingCount){}
    public record SourceView(String conceptId,String conceptName,Long portfolioSelectionId,
                             Integer selectionRevision,String marketSeedSnapshotId){}
    public record CurrentView(RunView run,VersionView version,SourceView source,boolean stale){}
}
