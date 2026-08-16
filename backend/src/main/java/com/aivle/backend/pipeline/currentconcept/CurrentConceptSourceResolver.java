package com.aivle.backend.pipeline.currentconcept;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import org.springframework.stereotype.Component;

/** One authority for product modules that consume the current refined concept. */
@Component
public class CurrentConceptSourceResolver {
    private final ConceptPortfolioSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository seeds;
    private final BmPlanPreparationService bmPlans;

    public CurrentConceptSourceResolver(ConceptPortfolioSelectionRepository selections,
            MarketAnalysisSeedSnapshotRepository seeds, BmPlanPreparationService bmPlans) {
        this.selections = selections; this.seeds = seeds; this.bmPlans = bmPlans;
    }

    public Source require(Long projectId, String userMessage) {
        Source value = currentOrNull(projectId);
        if (value == null) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE, userMessage);
        return value;
    }

    public Source currentOrNull(Long projectId) {
        ConceptPortfolioSelection selection = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        if (selection == null) return null;
        MarketAnalysisSeedSnapshot seed = seeds
            .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId())
            .filter(value -> projectId.equals(value.getProjectId()))
            .filter(value -> "CONCEPT_PORTFOLIO_V2".equals(value.getSourceType()))
            .orElse(null);
        return seed == null ? null : new Source(selection, seed, bmPlans.current(projectId));
    }

    public Binding binding(Source source) {
        if (source == null) return null;
        return new Binding(source.seed().getId(), source.selection().getId(),
            source.selection().getHypothesisRevision(), source.bm().revision());
    }

    public record Source(ConceptPortfolioSelection selection, MarketAnalysisSeedSnapshot seed,
                         BmPlanPreparationService.PlanView bm) { }
    public record Binding(String marketSeedSnapshotId, Long selectionId,
                          int selectionRevision, int bmPlanRevision) { }
}
