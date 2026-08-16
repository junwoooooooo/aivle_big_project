package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionMaterializationService.ContractViolation;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.*;
import tools.jackson.databind.node.*;

@Service
public class ConceptRefinementFinalizationMaterializationService {
    private final ConceptRefinementRoundRepository rounds;private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioHypothesisDecisionRepository hypotheses;
    private final ConceptLegalRegulatoryReportRepository reports;private final MarketAnalysisSeedSnapshotRepository seeds;
    private final BmPlanPreparationService bmPlans;private final ConceptRefinementDecisionContract decisions;
    private final ConceptRefinementFinalizationService finalization;private final ConceptPortfolioJsonHasher hasher;
    private final TaskRunService taskRuns;private final ObjectMapper mapper;private final Clock clock;
    public ConceptRefinementFinalizationMaterializationService(ConceptRefinementRoundRepository rounds,
            ConceptPortfolioSelectionRepository selections,ConceptPortfolioHypothesisDecisionRepository hypotheses,
            ConceptLegalRegulatoryReportRepository reports,
            MarketAnalysisSeedSnapshotRepository seeds,BmPlanPreparationService bmPlans,
            ConceptRefinementDecisionContract decisions,ConceptRefinementFinalizationService finalization,
            ConceptPortfolioJsonHasher hasher,TaskRunService taskRuns,ObjectMapper mapper,Clock clock){
        this.rounds=rounds;this.selections=selections;this.hypotheses=hypotheses;this.reports=reports;this.seeds=seeds;this.bmPlans=bmPlans;
        this.decisions=decisions;this.finalization=finalization;this.hasher=hasher;this.taskRuns=taskRuns;this.mapper=mapper;this.clock=clock;}
    public void complete(TaskRunService.Claim claim,TaskRunWorkerContext context,ExecutionResponse response,JsonNode result,JsonNode input){
        JsonNode binding=input.path("refinementFinalization");ConceptRefinementRound round=locked(binding);
        ConceptPortfolioSelection selection=selections.findLocked(Long.valueOf(context.subjectId())).orElseThrow(ContractViolation::new);
        if(!current(round,selection,context,binding))stale(claim,context,round,selection);
        JsonNode handoff=result.path("handoff"),market=handoff.path("marketAnalysisSeedSnapshot");
        require("PASS".equals(handoff.path("compatibility").asText()));require("market-analysis-seed-snapshot-v1".equals(market.path("contract").asText()));
        require("2.0".equals(market.path("schemaVersion").asText()));require(binding.path("finalMarketSeedSnapshotId").asText().equals(market.path("snapshotId").asText()));
        String snapshotHash=result.path("marketSeedSnapshotHash").asText();require(snapshotHash.equals(hasher.productionCompatibleHash(market)));
        ConceptRefinementFinalizationService.OutcomePlan finalPlan=finalization.outcome(round);
        validateOverlay(round,market,finalPlan.overlayNode());
        validateHypotheses(selection.getId(),market);
        ConceptLegalRegulatoryReport report=reports.findBySelectionIdAndStatusAndDeletedAtIsNull(selection.getId(),"CURRENT").orElseThrow(ContractViolation::new);
        MarketAnalysisSeedSnapshot saved=seeds.save(MarketAnalysisSeedSnapshot.createPortfolio(market.path("snapshotId").asText(),context.projectId(),selection.getId(),
            selection.getConceptId(),report.getId(),"2.0",market.path("sourceSnapshotHash").asText(),snapshotHash,mapper.writeValueAsString(market),context.ownerId(),Instant.now(clock)));
        ConceptRefinementFinal value=finalization.createFinal(context.ownerId(),context.projectId(),round,finalPlan.outcome(),
            saved.getId(),round.getAppliedSelectionRevision(),round.getAppliedBmPlanRevision(),finalPlan.overlayNode(),finalPlan.selectedChanges());
        selection.completeTask(context.taskRunId(),ConceptPortfolioSelectionStatus.READY_FOR_MARKET,false);
        round.finalized(value.getId(),saved.getId(),Instant.now(clock));taskRuns.adopt(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),
            mapper.writeValueAsString(result),context.inputHash(),response.resultSchemaVersion());}
    public void fail(TaskRunService.Claim claim,TaskRunWorkerContext context,String code,String reason,boolean retryable,JsonNode input){
        ConceptRefinementRound round=locked(input.path("refinementFinalization"));ConceptPortfolioSelection selection=selections.findLocked(Long.valueOf(context.subjectId())).orElseThrow(ContractViolation::new);
        selection.clearAuxiliaryTaskIfActive(context.taskRunId());round.finalizationFailed(context.taskRunId(),code);
        taskRuns.fail(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),code,reason,retryable);}
    private void validateOverlay(ConceptRefinementRound round,JsonNode market,ObjectNode overlay){
        MarketAnalysisSeedSnapshot source=seeds.findByIdAndDeletedAtIsNull(round.getSourceMarketSeedSnapshotId()).orElseThrow(ContractViolation::new);
        JsonNode baseline=mapper.readTree(source.getSnapshotJson()).path("selectedConcept");
        for(String field:List.of("targetUsers","featureSet")){JsonNode expected=overlay.has(field)?overlay.get(field):
            ("targetUsers".equals(field)?baseline.at("/identity/targetUsers"):baseline.at("/solution/featureSet"));
            JsonNode actual="targetUsers".equals(field)?market.at("/selectedConcept/identity/targetUsers"):market.at("/selectedConcept/solution/featureSet");require(expected.equals(actual));}}
    private void validateHypotheses(Long selectionId,JsonNode market){JsonNode values=market.path("finalHypotheses");require(values.isObject());
        Map<PortfolioHypothesisType,String> fields=new EnumMap<>(PortfolioHypothesisType.class);
        fields.put(PortfolioHypothesisType.TARGET_REGION,"targetRegion");fields.put(PortfolioHypothesisType.REVENUE_MODEL,"revenueModel");
        fields.put(PortfolioHypothesisType.PRICE,"price");fields.put(PortfolioHypothesisType.CHANNELS,"channels");
        fields.put(PortfolioHypothesisType.DIFFERENTIATORS,"differentiators");fields.put(PortfolioHypothesisType.PRE_MARKET_SOM_SHARE,"preMarketSomShare");
        fields.put(PortfolioHypothesisType.PRE_MARKET_SOM,"preMarketSom");
        Map<PortfolioHypothesisType,ConceptPortfolioHypothesisDecision> latest=new EnumMap<>(PortfolioHypothesisType.class);
        hypotheses.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(selectionId)
            .forEach(value->latest.putIfAbsent(value.getHypothesisType(),value));require(latest.size()==fields.size());
        fields.forEach((type,field)->{ConceptPortfolioHypothesisDecision value=latest.get(type);require(value!=null&&value.getFinalValueJson()!=null);
            require(mapper.readTree(value.getFinalValueJson()).equals(values.path(field).path("value")));});}
    private boolean current(ConceptRefinementRound r,ConceptPortfolioSelection s,TaskRunWorkerContext c,JsonNode b){
        return r.getState()==ConceptRefinementRound.State.FINALIZING&&Objects.equals(r.getFinalizationTaskRunId(),c.taskRunId())
            &&b.path("roundId").asLong()==r.getId()&&Objects.equals(b.path("finalizationHash").asText(),r.getFinalizationHash())
            &&s.isCurrent()&&s.getHypothesisRevision()==r.getAppliedSelectionRevision()&&bmPlans.current(c.projectId()).revision()==r.getAppliedBmPlanRevision()
            &&reports.findBySelectionIdAndStatusAndDeletedAtIsNull(s.getId(),"CURRENT").isPresent()
            &&seeds.findByIdAndDeletedAtIsNull(r.getSourceMarketSeedSnapshotId()).filter(v->v.getStaleAt()!=null
                &&Objects.equals(v.getProjectId(),c.projectId())&&Objects.equals(v.getPortfolioSelectionId(),s.getId())
                &&"CONCEPT_PORTFOLIO_V2".equals(v.getSourceType())).isPresent();}
    private ConceptRefinementRound locked(JsonNode b){return rounds.findByIdForUpdate(b.path("roundId").asLong()).orElseThrow(ContractViolation::new);}
    private void stale(TaskRunService.Claim claim,TaskRunWorkerContext c,ConceptRefinementRound r,ConceptPortfolioSelection s){r.markStale();s.clearAuxiliaryTaskIfActive(c.taskRunId());
        taskRuns.fail(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),"MODULE_INPUT_STALE","LATE_OR_DUPLICATE_RESULT",false);throw new ConceptRefinementMaterializationService.StaleResult();}
    private static void require(boolean c){if(!c)throw new ContractViolation();}
}
