package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.*;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import tools.jackson.databind.node.*;

@Service
public class ConceptRefinementFinalizationService {
    private final ProjectRepository projects; private final ConceptRefinementRoundRepository rounds;
    private final ConceptRefinementFinalRepository finals; private final ConceptRefinementDecisionContract decisions;
    private final ConceptRefinementLineageGuard lineage; private final MarketAnalysisSeedSnapshotRepository seeds;
    private final BmPlanPreparationService bmPlans; private final ConceptPortfolioSelectionService selectionService;
    private final ConceptPortfolioJsonHasher hasher; private final ObjectMapper mapper; private final Clock clock;
    public ConceptRefinementFinalizationService(ProjectRepository projects,ConceptRefinementRoundRepository rounds,
            ConceptRefinementFinalRepository finals,ConceptRefinementDecisionContract decisions,
            ConceptRefinementLineageGuard lineage,MarketAnalysisSeedSnapshotRepository seeds,
            BmPlanPreparationService bmPlans,ConceptPortfolioSelectionService selectionService,
            ConceptPortfolioJsonHasher hasher,ObjectMapper mapper,Clock clock){
        this.projects=projects;this.rounds=rounds;this.finals=finals;this.decisions=decisions;this.lineage=lineage;
        this.seeds=seeds;this.bmPlans=bmPlans;this.selectionService=selectionService;this.hasher=hasher;this.mapper=mapper;this.clock=clock;}

    @Transactional
    public FinalView finalizeRound(Long ownerId,Long projectId,String key,Integer expectedRound,String expectedDecisionHash){
        owned(ownerId,projectId); key=validKey(key); ConceptRefinementRound round=currentRound(projectId);
        if(expectedRound==null||expectedRound!=round.getRoundNumber())throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        if(!Objects.equals(expectedDecisionHash,round.getDecisionHash())){
            if(key.equals(round.getFinalizationIdempotencyKey()))throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);}
        if(round.getFinalizationIdempotencyKey()!=null&&round.getState()!=ConceptRefinementRound.State.FINALIZATION_FAILED){
            if(key.equals(round.getFinalizationIdempotencyKey()))
                return view(finals.findByRoundIdAndDeletedAtIsNull(round.getId()).orElse(null),round);
            throw unavailable();}
        OutcomePlan outcome=outcome(round); String identity=finalizationHash(round,outcome);
        if(round.getFinalizationIdempotencyKey()!=null){
            if(key.equals(round.getFinalizationIdempotencyKey())){
                if(!identity.equals(round.getFinalizationHash()))throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
                return view(finals.findByRoundIdAndDeletedAtIsNull(round.getId()).orElse(null),round);}
            if(round.getState()!=ConceptRefinementRound.State.FINALIZATION_FAILED||round.getFinalizationAttempt()>=3)
                throw unavailable();}
        boolean current=java.util.Set.of(ConceptRefinementRound.State.KEEP_CURRENT,ConceptRefinementRound.State.NO_CHANGES)
            .contains(round.getState())?lineage.preApplyCurrent(ownerId,projectId,round):lineage.postApplyCurrent(projectId,round);
        if(!current){round.markStale();throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);}
        if(outcome.hypotheses&&!hasCurrentReport(ownerId,projectId,round.getSelectionId()))
            selectionService.finalizeReport(ownerId,projectId,round.getSelectionId());
        else if(outcome.overlay&&!hasCurrentReport(ownerId,projectId,round.getSelectionId()))
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        if(!outcome.newSeed()){
            int selectionRevision=outcome.resolved?round.getSourceSelectionRevision():round.getAppliedSelectionRevision();
            int bmRevision=outcome.resolved?round.getSourceBmPlanRevision():round.getAppliedBmPlanRevision();
            if(outcome.resolved)round.recordResolvedLineage(selectionRevision,bmRevision);
            round.startFinalization(key,identity,null,Instant.now(clock));
            ConceptRefinementFinal saved=createFinal(ownerId,projectId,round,outcome.outcome,
                round.getSourceMarketSeedSnapshotId(),selectionRevision,bmRevision,outcome.overlayNode,outcome.selectedChanges);
            round.finalized(saved.getId(),saved.getFinalMarketSeedSnapshotId(),Instant.now(clock)); return view(saved,round);}
        String snapshotId=UUID.randomUUID().toString(); ObjectNode binding=binding(round,identity,snapshotId);
        TaskRun task=selectionService.finalizeMarketSeedFromRefinement(ownerId,projectId,round.getSelectionId(),
            round.getSourceMarketSeedSnapshotId(),outcome.overlayNode,binding,key,snapshotId);
        if(round.getState()==ConceptRefinementRound.State.FINALIZATION_FAILED)
            round.retryFinalization(key,identity,task.getId(),Instant.now(clock));
        else round.startFinalization(key,identity,task.getId(),Instant.now(clock));
        return view(null,round);
    }

    ConceptRefinementFinal createFinal(Long userId,Long projectId,ConceptRefinementRound round,
            ConceptRefinementFinal.Outcome outcome,String finalSeedId,int selectionRevision,int bmRevision,
            ObjectNode overlay,ArrayNode selectedChanges){
        MarketAnalysisSeedSnapshot seed=seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull(finalSeedId)
            .orElseThrow(()->new BusinessException(ErrorCode.MODULE_INPUT_STALE));
        BmPlanPreparationService.PlanView bm=bmPlans.current(projectId);
        if(bm.revision()!=bmRevision)throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        JsonNode snapshot=mapper.readTree(seed.getSnapshotJson()); ObjectNode json=mapper.createObjectNode();
        json.put("contract","concept-refinement-final-v1");json.put("schemaVersion","1.0");json.put("outcome",outcome.name());
        json.put("round",round.getRoundNumber());json.put("policyVersion",round.getPolicyVersion());
        ObjectNode source=json.putObject("source");source.put("businessValidationSessionId",round.getBusinessValidationSessionId());
        source.put("marketSeedSnapshotId",round.getSourceMarketSeedSnapshotId());source.put("marketVersionId",round.getSourceMarketVersionId());
        source.put("bmVersionId",round.getSourceBmVersionId());source.put("selectionRevision",round.getSourceSelectionRevision());source.put("bmPlanRevision",round.getSourceBmPlanRevision());
        ObjectNode fin=json.putObject("final");fin.put("marketSeedSnapshotId",finalSeedId);fin.put("selectionRevision",selectionRevision);fin.put("bmPlanRevision",bmRevision);
        fin.put("legalReportId",seed.getLegalReportId());json.set("selectedChanges",selectedChanges.deepCopy());json.set("overlay",overlay.deepCopy());
        json.set("selectedConcept",snapshot.path("selectedConcept").deepCopy());json.set("finalHypotheses",snapshot.path("finalHypotheses").deepCopy());
        json.set("businessModelPlan",bm.plan().deepCopy());String hash=hasher.hash(json);
        return finals.save(ConceptRefinementFinal.create(projectId,round,outcome,finalSeedId,selectionRevision,bmRevision,
            overlay.toString(),json.toString(),hash,userId,Instant.now(clock)));}

    public FinalView current(Long ownerId,Long projectId){ownedRead(ownerId,projectId);ConceptRefinementFinal value=finals
        .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        return value==null?null:view(value,currentRound(projectId));}
    private FinalView view(ConceptRefinementFinal value,ConceptRefinementRound round){if(value==null)return new FinalView(round.getState().name(),null,true,null);
        boolean stale=!lineage.postApplyCurrent(value.getProjectId(),round)||seeds
            .findByIdAndStaleAtIsNullAndDeletedAtIsNull(value.getFinalMarketSeedSnapshotId()).isEmpty();
        return new FinalView("FINALIZED",value.getOutcome().name(),stale,mapper.readTree(value.getFinalJson()));}
    private OutcomePlan outcome(ConceptRefinementRound round){
        if(round.getState()==ConceptRefinementRound.State.KEEP_CURRENT)return new OutcomePlan(ConceptRefinementFinal.Outcome.KEEP_CURRENT,true,false,false,mapper.createObjectNode(),mapper.createArrayNode());
        if(round.getState()==ConceptRefinementRound.State.NO_CHANGES)return new OutcomePlan(ConceptRefinementFinal.Outcome.NO_CHANGES,true,false,false,mapper.createObjectNode(),mapper.createArrayNode());
        if(!java.util.Set.of(ConceptRefinementRound.State.APPLIED_PENDING_FINALIZATION,ConceptRefinementRound.State.FINALIZATION_FAILED).contains(round.getState()))throw unavailable();
        ObjectNode plan=decisions.applicationPlan(round);ObjectNode overlay=(ObjectNode)plan.path("overlay");boolean hypotheses=!plan.path("hypotheses").isEmpty();
        ArrayNode changes=(ArrayNode)mapper.readTree(round.getDecisionJson()).path("selectedProposals").deepCopy();
        return new OutcomePlan(ConceptRefinementFinal.Outcome.REFINED,false,hypotheses,!overlay.isEmpty(),overlay,changes);}
    private String finalizationHash(ConceptRefinementRound r,OutcomePlan o){ObjectNode n=mapper.createObjectNode();n.put("roundId",r.getId());n.put("outcome",o.outcome.name());
        if(r.getDecisionHash()!=null)n.put("decisionHash",r.getDecisionHash());if(r.getApplicationHash()!=null)n.put("applicationHash",r.getApplicationHash());
        n.put("sourceSeed",r.getSourceMarketSeedSnapshotId());n.put("sourceSelectionRevision",r.getSourceSelectionRevision());n.put("sourceBmPlanRevision",r.getSourceBmPlanRevision());
        if(r.getAppliedSelectionRevision()!=null)n.put("finalSelectionRevision",r.getAppliedSelectionRevision());if(r.getAppliedBmPlanRevision()!=null)n.put("finalBmPlanRevision",r.getAppliedBmPlanRevision());n.set("overlay",o.overlayNode);return hasher.hash(n);}
    private ObjectNode binding(ConceptRefinementRound r,String hash,String snapshotId){ObjectNode n=mapper.createObjectNode();n.put("roundId",r.getId());n.put("finalizationHash",hash);
        n.put("decisionHash",r.getDecisionHash());n.put("applicationHash",r.getApplicationHash());n.put("expectedSelectionRevision",r.getAppliedSelectionRevision());n.put("expectedBmPlanRevision",r.getAppliedBmPlanRevision());n.put("sourceMarketSeedSnapshotId",r.getSourceMarketSeedSnapshotId());n.put("finalMarketSeedSnapshotId",snapshotId);return n;}
    private boolean hasCurrentReport(Long owner,Long project,Long selection){try{selectionService.currentReport(owner,project,selection);return true;}catch(BusinessException e){return false;}}
    private ConceptRefinementRound currentRound(Long project){return rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(project).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private void owned(Long u,Long p){projects.findByIdForUpdate(p).filter(v->v.getOwner().getId().equals(u)).orElseThrow(()->new BusinessException(ErrorCode.PROJECT_NOT_FOUND));}
    private void ownedRead(Long u,Long p){projects.findByIdAndOwnerIdAndDeletedAtIsNull(p,u).orElseThrow(()->new BusinessException(ErrorCode.PROJECT_NOT_FOUND));}
    private String validKey(String k){if(k==null||k.isBlank()||k.strip().length()>128)throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);return k.strip();}
    private BusinessException unavailable(){return new BusinessException(ErrorCode.INVALID_REQUEST,"finalization unavailable");}
    private record OutcomePlan(ConceptRefinementFinal.Outcome outcome,boolean resolved,boolean hypotheses,boolean overlay,ObjectNode overlayNode,ArrayNode selectedChanges){boolean newSeed(){return hypotheses||overlay;}}
    public record FinalView(String state,String outcome,boolean stale,JsonNode value){}
}
