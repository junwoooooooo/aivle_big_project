package com.aivle.backend.pipeline.marketinterview;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.refinement.ConceptRefinementFinal;
import com.aivle.backend.pipeline.refinement.ConceptRefinementFinalRepository;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRound;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRoundRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Stage 4-only authority: only a current finalized refinement can feed an interview. */
@Component
public class MarketInterviewSourceResolver {
    private final ConceptRefinementFinalRepository finals;
    private final ConceptRefinementRoundRepository rounds;
    private final ConceptPortfolioSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository seeds;
    private final BmPlanPreparationService bmPlans;
    private final ObjectMapper mapper;

    public MarketInterviewSourceResolver(ConceptRefinementFinalRepository finals,
            ConceptRefinementRoundRepository rounds,
            ConceptPortfolioSelectionRepository selections,
            MarketAnalysisSeedSnapshotRepository seeds,
            BmPlanPreparationService bmPlans, ObjectMapper mapper) {
        this.finals = finals; this.rounds = rounds; this.selections = selections;
        this.seeds = seeds; this.bmPlans = bmPlans; this.mapper = mapper;
    }

    public Source require(Long projectId) {
        Source value = currentOrNull(projectId);
        if (value == null) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
            "컨셉 다듬기를 최종 확정한 뒤 시장 인터뷰를 시작해 주세요.");
        return value;
    }

    public Source currentOrNull(Long projectId) {
        ConceptRefinementFinal fin = finals
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        if (fin == null) return null;
        ConceptRefinementRound round = rounds.findById(fin.getRoundId()).filter(value -> !value.isDeleted()).orElse(null);
        ConceptPortfolioSelection selection = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        if (round == null || selection == null || round.getState() != ConceptRefinementRound.State.FINALIZED
                || !Objects.equals(round.getFinalId(), fin.getId())
                || !Objects.equals(round.getFinalMarketSeedSnapshotId(), fin.getFinalMarketSeedSnapshotId())
                || !Objects.equals(round.getBusinessValidationSessionId(), fin.getSourceBusinessValidationSessionId())
                || !Objects.equals(selection.getId(), fin.getSelectionId())
                || selection.getHypothesisRevision() != fin.getFinalSelectionRevision()) return null;
        MarketAnalysisSeedSnapshot seed = seeds
            .findByIdAndStaleAtIsNullAndDeletedAtIsNull(fin.getFinalMarketSeedSnapshotId())
            .filter(value -> Objects.equals(projectId, value.getProjectId()))
            .filter(value -> Objects.equals(selection.getId(), value.getPortfolioSelectionId()))
            .orElse(null);
        if (seed == null) return null;
        BmPlanPreparationService.PlanView bm;
        try { bm = bmPlans.current(projectId); }
        catch (BusinessException unavailable) { return null; }
        if (bm.revision() != fin.getFinalBmPlanRevision()) return null;
        JsonNode document;
        try { document = mapper.readTree(fin.getFinalJson()); }
        catch (RuntimeException invalid) { return null; }
        if (document == null || !document.isObject()
                || !"concept-refinement-final-v1".equals(document.path("contract").asText())
                || !document.path("selectedConcept").isObject()
                || !document.path("finalHypotheses").isObject()
                || !fin.getFinalMarketSeedSnapshotId().equals(document.path("final").path("marketSeedSnapshotId").asText())
                || fin.getFinalSelectionRevision() != document.path("final").path("selectionRevision").asInt(-1)
                || fin.getFinalBmPlanRevision() != document.path("final").path("bmPlanRevision").asInt(-1)) return null;
        return new Source(fin, round, seed, selection, bm, document);
    }

    public record Source(ConceptRefinementFinal refinementFinal, ConceptRefinementRound round,
                         MarketAnalysisSeedSnapshot seed, ConceptPortfolioSelection selection,
                         BmPlanPreparationService.PlanView bm, JsonNode finalDocument) { }
}
