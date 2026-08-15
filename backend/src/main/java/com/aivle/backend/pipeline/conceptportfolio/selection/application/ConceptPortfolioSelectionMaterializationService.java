package com.aivle.backend.pipeline.conceptportfolio.selection.application;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConceptPortfolioSelectionMaterializationService {
    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioHypothesisDecisionRepository hypotheses;
    private final ConceptPortfolioDeltaLegalReviewRepository deltas;
    private final ConceptLegalRegulatoryReportRepository reports;
    private final MarketAnalysisSeedSnapshotRepository marketSeeds;
    private final ConceptPortfolioSelectionService selectionService;
    private final ConceptPortfolioJsonHasher hasher;
    private final TaskRunService taskRuns;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ConceptPortfolioSelectionMaterializationService(ConceptPortfolioSelectionRepository selections,
            ConceptPortfolioHypothesisDecisionRepository hypotheses,
            ConceptPortfolioDeltaLegalReviewRepository deltas,
            ConceptLegalRegulatoryReportRepository reports,
            MarketAnalysisSeedSnapshotRepository marketSeeds,
            ConceptPortfolioSelectionService selectionService, ConceptPortfolioJsonHasher hasher,
            TaskRunService taskRuns, ObjectMapper mapper, Clock clock) {
        this.selections=selections; this.hypotheses=hypotheses; this.deltas=deltas;
        this.reports=reports; this.marketSeeds=marketSeeds; this.selectionService=selectionService;
        this.hasher=hasher; this.taskRuns=taskRuns; this.mapper=mapper; this.clock=clock;
    }

    @Transactional
    public String complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        ConceptPortfolioSelection selection=locked(context);
        JsonNode result=validate(response.result());
        String action=result.path("action").asText();
        JsonNode input=mapper.readTree(context.inputSnapshot());
        require(action.equals(input.path("action").asText()));
        require(input.path("expectedHypothesisRevision").isIntegralNumber()
            && input.path("expectedHypothesisRevision").asInt() == selection.getHypothesisRevision());
        switch(action) {
            case "PREPARE_HYPOTHESES" -> {
                persistInitial(selection,result.path("hypotheses"));
                selection.completeTask(context.taskRunId(),ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION,true);
                adopt(claim,context,response);
            }
            case "CONFIRM_HYPOTHESES" -> {
                applyHypotheses(selection,result.path("hypotheses"),context.ownerId());
                boolean allReady=selectionService.latestRequired(selection.getId()).stream()
                    .allMatch(value->("ACCEPTED".equals(value.getDecisionStatus())||"USER_EDITED_ACCEPTED".equals(value.getDecisionStatus()))
                        && value.getFinalValueJson()!=null&&"VALID".equals(value.getSemanticStatus()));
                boolean deltaRequired=selectionService.latestRequired(selection.getId()).stream()
                    .anyMatch(value->value.isDeltaLegalRequired()&&"PENDING".equals(value.getLegalReviewStatus()));
                ConceptPortfolioSelectionStatus next=!allReady?ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION:
                    deltaRequired?ConceptPortfolioSelectionStatus.DELTA_LEGAL_PENDING:ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT;
                selection.completeTask(context.taskRunId(),next,true); adopt(claim,context,response);
                if(allReady&&deltaRequired) selectionService.queueDelta(context.ownerId(),selection,
                    context.idempotencyKey()+":delta");
            }
            case "PROPOSE_ALTERNATIVE" -> {
                JsonNode item=result.path("alternative"); require(item.isObject());
                PortfolioHypothesisType type=PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText());
                int version=item.path("proposalVersion").asInt();
                hypotheses.save(fromJson(selection,item,type,version,null));
                selection.completeTask(context.taskRunId(),ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION,true);
                staleDependents(selection.getId()); adopt(claim,context,response);
            }
            case "DELTA_LEGAL" -> {
                JsonNode delta=result.path("deltaLegalResult"); require(delta.isObject());
                boolean approved=delta.path("approved").asBoolean();
                String json=mapper.writeValueAsString(result);
                deltas.save(ConceptPortfolioDeltaLegalReview.create(selection,context.taskRunId(),
                    input.path("expectedHypothesisRevision").asInt(), delta.path("reviewToken").asText(),
                    mapper.writeValueAsString(delta.path("hypothesisTypes")),
                    delta.path("status").asText(),approved,json,hasher.hash(result)));
                if(approved) applyHypotheses(selection,result.path("hypotheses"),context.ownerId());
                boolean allReady = approved && selectionService.latestRequired(selection.getId()).stream()
                    .allMatch(ConceptPortfolioHypothesisDecision::ready);
                selection.completeTask(context.taskRunId(),allReady?ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT:
                    ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED,false); adopt(claim,context,response);
            }
            case "BUILD_HANDOFF" -> {
                JsonNode handoff=result.path("handoff"); JsonNode market=handoff.path("marketAnalysisSeedSnapshot");
                require("PASS".equals(handoff.path("compatibility").asText()));
                require("market-analysis-seed-snapshot-v1".equals(market.path("contract").asText()));
                require("2.0".equals(market.path("schemaVersion").asText()));
                var report=reports.findBySelectionIdAndStatusAndDeletedAtIsNull(selection.getId(),"CURRENT")
                    .orElseThrow(ContractViolation::new);
                String id=market.path("snapshotId").asText(); String snapshotHash=result.path("marketSeedSnapshotHash").asText();
                require(snapshotHash.equals(hasher.productionCompatibleHash(market)));
                marketSeeds.save(MarketAnalysisSeedSnapshot.createPortfolio(id,selection.getProjectId(),selection.getId(),
                    selection.getConceptId(),report.getId(),"2.0",market.path("sourceSnapshotHash").asText(),snapshotHash,
                    mapper.writeValueAsString(market),context.ownerId(),Instant.now(clock)));
                selection.completeTask(context.taskRunId(),ConceptPortfolioSelectionStatus.READY_FOR_MARKET,false);
                adopt(claim,context,response);
            }
            default -> throw new ContractViolation();
        }
        return action;
    }

    @Transactional
    public void fail(TaskRunService.Claim claim,TaskRunWorkerContext context,String code,String reason,boolean retryable){
        taskRuns.assertActiveClaim(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken());
        ConceptPortfolioSelection selection=locked(context);
        String action=mapper.readTree(context.inputSnapshot()).path("action").asText();
        selection.failTask(context.taskRunId(),"DELTA_LEGAL".equals(action)?ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED:
            ConceptPortfolioSelectionStatus.FAILED,code); taskRuns.fail(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),code,reason,retryable);
    }
    private void persistInitial(ConceptPortfolioSelection s,JsonNode array){require(array.isArray()&&array.size()==7);Set<String> types=new HashSet<>();
        for(JsonNode item:array){PortfolioHypothesisType type=PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText());types.add(type.name());hypotheses.save(fromJson(s,item,type,1,null));}
        require(types.size()==7&&types.contains("TARGET_REGION"));}
    private void applyHypotheses(ConceptPortfolioSelection s,JsonNode array,Long user){require(array.isArray()&&array.size()==7);
        for(JsonNode item:array){PortfolioHypothesisType type=PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText());
            ConceptPortfolioHypothesisDecision current=hypotheses.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(s.getId(),type).orElseThrow(ContractViolation::new);
            current.apply(nullableJson(item.get("finalValue")),item.path("source").asText(),item.path("decisionStatus").asText(),item.path("locked").asBoolean(),
                item.path("semanticStatus").asText(),item.path("semanticReason").isNull()?null:item.path("semanticReason").asText(),item.path("legalImpact").asText(),
                item.path("legalReviewStatus").asText(),item.path("deltaLegalRequired").asBoolean(),user,
                item.path("finalValue").isNull()?null:Instant.now(clock));}}
    private ConceptPortfolioHypothesisDecision fromJson(ConceptPortfolioSelection s,JsonNode item,PortfolioHypothesisType type,int version,Long user){
        return ConceptPortfolioHypothesisDecision.create(s.getId(),s.getProjectId(),s.getConceptId(),type,mapper.writeValueAsString(item.path("proposedValue")),
            nullableJson(item.get("finalValue")),item.path("source").asText(),item.path("decisionStatus").asText(),version,item.path("locked").asBoolean(),
            item.path("semanticStatus").asText("UNASSESSED"),item.path("semanticReason").isMissingNode()||item.path("semanticReason").isNull()?null:item.path("semanticReason").asText(),
            item.path("legalImpact").asText("NONE"),item.path("legalReviewStatus").asText("NOT_REQUIRED"),item.path("deltaLegalRequired").asBoolean(false),user,null);}
    private String nullableJson(JsonNode value){return value==null||value.isNull()?null:mapper.writeValueAsString(value);}
    private JsonNode validate(JsonNode result){require(result!=null&&result.isObject());require("concept-portfolio-v2-selection-action-result-v1".equals(result.path("contract").asText()));require("1.0".equals(result.path("schemaVersion").asText()));return result;}
    private ConceptPortfolioSelection locked(TaskRunWorkerContext c){ConceptPortfolioSelection s=selections.findLocked(Long.valueOf(c.subjectId())).orElseThrow(ContractViolation::new);
        require(s.isCurrent()&&s.getProjectId().equals(c.projectId())&&c.taskRunId().equals(s.getActiveTaskRunId()));return s;}
    private void adopt(TaskRunService.Claim claim,TaskRunWorkerContext c,ExecutionResponse r){taskRuns.adopt(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),mapper.writeValueAsString(r.result()),c.inputHash(),r.resultSchemaVersion());}
    private void staleDependents(Long id){reports.findAllBySelectionIdAndStatusAndDeletedAtIsNull(id,"CURRENT").forEach(ConceptLegalRegulatoryReport::markStale);marketSeeds.findAllByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(id).forEach(v->v.markStale(Instant.now(clock)));}
    private static void require(boolean condition){if(!condition)throw new ContractViolation();}
    public static final class ContractViolation extends RuntimeException { }
}
