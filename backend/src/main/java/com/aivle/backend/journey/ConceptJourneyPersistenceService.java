package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConceptJourneyPersistenceService {
    private final ConceptGenerationRunRepository generationRuns; private final ConceptRepository concepts;
    private final ConceptVersionRepository conceptVersions; private final QuickAssessmentRunRepository quickRuns;
    private final QuickAssessmentRepository quickAssessments; private final DetailedAnalysisRunRepository detailedRuns;
    private final DetailedAnalysisRepository detailedAnalyses; private final JourneyFinancialAnalysisRepository financials;
    private final ObjectMapper mapper;

    public ConceptJourneyPersistenceService(ConceptGenerationRunRepository generationRuns, ConceptRepository concepts,
            ConceptVersionRepository conceptVersions, QuickAssessmentRunRepository quickRuns,
            QuickAssessmentRepository quickAssessments, DetailedAnalysisRunRepository detailedRuns,
            DetailedAnalysisRepository detailedAnalyses, JourneyFinancialAnalysisRepository financials, ObjectMapper mapper) {
        this.generationRuns=generationRuns; this.concepts=concepts; this.conceptVersions=conceptVersions;
        this.quickRuns=quickRuns; this.quickAssessments=quickAssessments; this.detailedRuns=detailedRuns;
        this.detailedAnalyses=detailedAnalyses; this.financials=financials; this.mapper=mapper;
    }

    @Transactional
    public void completeGeneration(Long runId, JsonNode result) {
        ConceptGenerationRun run=generationRuns.findById(runId).orElseThrow(this::notFound);
        if (run.getState()==ConceptAiRunBase.State.SUCCEEDED) return;
        if (!conceptVersions.findCurrentForIdea(run.getProject().getId(), run.getIdeaVersion().getId()).isEmpty()) {
            run.succeed(result.toString()); return;
        }
        int order=1;
        for (JsonNode item: result.get("concepts")) {
            Concept concept=concepts.save(Concept.create(run.getProject(), run.getIdeaVersion(), run, order++));
            conceptVersions.save(ConceptVersion.create(run.getProject(), run.getIdeaVersion(), concept,
                text(item,"name"), text(item,"oneLineSummary"), text(item,"targetCustomer"), text(item,"problem"),
                text(item,"solution"), text(item,"valueProposition"), text(item,"revenueModel"),
                item.get("keyFeatures").toString(), item.get("differentiators").toString(),
                item.get("assumptions").toString(), item.get("risks").toString()));
        }
        run.succeed(result.toString());
    }

    @Transactional
    public void publishEligible(ConceptEligibilityBatch batch, ConceptGenerationRun run, List<ConceptDraft> drafts) {
        if (!conceptVersions.findByEligibilityBatchIdAndEligibilityStatusAndDeletedAtIsNullOrderByConceptDisplayOrder(
            batch.getId(), "ELIGIBLE").isEmpty()) return;
        int order=1;
        for (ConceptDraft draft : drafts.stream().filter(value -> value.getEligibilityStatus() == ConceptDraft.EligibilityStatus.ELIGIBLE).limit(batch.getTargetEligibleCount()).toList()) {
            JsonNode item=mapper.readTree(draft.getDraftJson());
            Concept concept=concepts.save(Concept.create(batch.getProject(), batch.getIdeaOriginVersion().getSourceIdeaVersion(), run, order++));
            conceptVersions.save(ConceptVersion.eligible(batch.getProject(), batch.getIdeaOriginVersion().getSourceIdeaVersion(), concept, batch,
                text(item,"conceptName"), item.get("targetSegment").toString(), text(item,"positioning"),
                item.get("featureSet").toString(), item.get("pricing").toString(), item.get("revenueModel").toString(),
                item.get("channels").toString(), item.get("operatingModel").toString(), item.get("newAssumptions").toString(),
                item.get("newBusinessActivities").toString(), item.get("originTrace").toString(), item.get("legalTrace").toString()));
        }
        run.succeed(mapper.createObjectNode().put("batchId",batch.getId()).put("eligibleCount",batch.getTargetEligibleCount()).toString());
        generationRuns.save(run);
    }

    @Transactional
    public void completeQuick(Long runId, JsonNode result) {
        QuickAssessmentRun run=quickRuns.findById(runId).orElseThrow(this::notFound);
        if (run.getState()==ConceptAiRunBase.State.SUCCEEDED) return;
        if (quickAssessments.findByRunIdAndDeletedAtIsNullOrderByOverallScoreDesc(runId).isEmpty()) {
            for (JsonNode item: result.get("assessments")) {
                Long conceptId=item.get("conceptVersionId").asLong();
                ConceptVersion concept=conceptVersions.findById(conceptId).filter(v -> v.getProject().getId().equals(run.getProject().getId())
                    && v.getIdeaVersion().getId().equals(run.getIdeaVersion().getId())).orElseThrow(this::invalid);
                quickAssessments.save(QuickAssessment.create(run.getProject(), run.getIdeaVersion(), run, concept,
                    score(item,"market"), score(item,"customerValue"), score(item,"feasibility"), score(item,"differentiation"),
                    score(item,"revenuePotential"), score(item,"legalRisk"), item.get("overallScore").decimalValue(),
                    text(item,"summary"), item.get("strengths").toString(), item.get("weaknesses").toString()));
            }
        }
        run.succeed(result.toString());
    }

    @Transactional
    public void completeDetailed(Long runId, JsonNode result, List<ConceptJourneyService.FinancialInput> inputs) {
        DetailedAnalysisRun run=detailedRuns.findById(runId).orElseThrow(this::notFound);
        if (run.getState()==ConceptAiRunBase.State.SUCCEEDED) return;
        Map<Long, ConceptJourneyService.FinancialInput> financeByConcept=new HashMap<>();
        for (var input: inputs) financeByConcept.put(input.conceptVersionId(), input);
        if (detailedAnalyses.findByRunIdAndDeletedAtIsNullOrderById(runId).isEmpty()) {
            for (JsonNode item: result.get("analyses")) {
                Long conceptId=item.get("conceptVersionId").asLong();
                ConceptVersion concept=conceptVersions.findById(conceptId).filter(v -> v.getProject().getId().equals(run.getProject().getId())
                    && v.getIdeaVersion().getId().equals(run.getIdeaVersion().getId())).orElseThrow(this::invalid);
                detailedAnalyses.save(DetailedAnalysis.create(run.getProject(), run.getIdeaVersion(), run, concept,
                    text(item,"marketAnalysis"), text(item,"customerAnalysis"), text(item,"businessModelAnalysis"),
                    text(item,"operationAnalysis"), text(item,"riskAnalysis"), text(item,"recommendation"),
                    item.get("assumptions").toString(), item.get("researchNeeds").toString()));
                saveFinancial(run, concept, Objects.requireNonNull(financeByConcept.get(conceptId), "financial input"));
            }
        }
        run.succeed(result.toString());
    }

    @Transactional
    public void failGeneration(Long id, String error) { generationRuns.findById(id).ifPresent(run -> run.fail(error)); }
    @Transactional
    public void failQuick(Long id, String error) { quickRuns.findById(id).ifPresent(run -> run.fail(error)); }
    @Transactional
    public void failDetailed(Long id, String error) { detailedRuns.findById(id).ifPresent(run -> run.fail(error)); }

    private void saveFinancial(DetailedAnalysisRun run, ConceptVersion concept, ConceptJourneyService.FinancialInput input) {
        BigDecimal price=positive(input.unitPrice()); BigDecimal variable=nonNegative(input.variableCostPerCustomer());
        BigDecimal fixed=nonNegative(input.monthlyFixedCost()); BigDecimal investment=nonNegative(input.initialInvestment());
        if (input.monthlyCustomers()<0 || price.compareTo(variable)<=0) throw invalid();
        BigDecimal customers=BigDecimal.valueOf(input.monthlyCustomers());
        BigDecimal revenue=price.multiply(customers); BigDecimal variableTotal=variable.multiply(customers);
        BigDecimal total=fixed.add(variableTotal); BigDecimal profit=revenue.subtract(total);
        int breakEven=fixed.divide(price.subtract(variable),0,RoundingMode.CEILING).intValueExact();
        BigDecimal payback=profit.signum()>0 ? investment.divide(profit,2,RoundingMode.HALF_UP) : null;
        var inputJson=mapper.createObjectNode(); inputJson.put("unitPrice",price); inputJson.put("monthlyCustomers",input.monthlyCustomers());
        inputJson.put("variableCostPerCustomer",variable); inputJson.put("monthlyFixedCost",fixed); inputJson.put("initialInvestment",investment);
        var result=mapper.createObjectNode(); result.put("monthlyRevenue",revenue); result.put("monthlyVariableCost",variableTotal);
        result.put("monthlyTotalCost",total); result.put("monthlyOperatingProfit",profit); result.put("breakEvenCustomers",breakEven);
        if(payback==null) result.putNull("paybackMonths"); else result.put("paybackMonths",payback);
        financials.save(JourneyFinancialAnalysis.create(run.getProject(),run.getIdeaVersion(),concept,concept.getName(),price,
            input.monthlyCustomers(),variable,fixed,investment,revenue,variableTotal,total,profit,breakEven,payback,inputJson.toString(),result.toString()));
    }
    private BigDecimal positive(BigDecimal value) { if(value==null||value.signum()<=0) throw invalid(); return value; }
    private BigDecimal nonNegative(BigDecimal value) { if(value==null||value.signum()<0) throw invalid(); return value; }
    private int score(JsonNode node,String field) { int value=node.get(field).asInt(); if(value<0||value>100) throw invalid(); return value; }
    private String text(JsonNode node,String field) { JsonNode value=node.get(field); if(value==null||!value.isTextual()||value.asText().isBlank()) throw invalid(); return value.asText(); }
    private BusinessException invalid() { return new BusinessException(ErrorCode.AI_RESULT_INVALID); }
    private BusinessException notFound() { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
}
