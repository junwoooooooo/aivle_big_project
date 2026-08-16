package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Separates original validation lineage from expected self-induced post-apply lineage. */
@Component
public class ConceptRefinementLineageGuard {
    private final BusinessValidationCoordinator validations;
    private final ConceptPortfolioSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository seeds;
    private final BmPlanPreparationService bmPlans;

    public ConceptRefinementLineageGuard(BusinessValidationCoordinator validations,
            ConceptPortfolioSelectionRepository selections,
            MarketAnalysisSeedSnapshotRepository seeds, BmPlanPreparationService bmPlans) {
        this.validations = validations; this.selections = selections;
        this.seeds = seeds; this.bmPlans = bmPlans;
    }

    public boolean preApplyCurrent(Long ownerId, Long projectId, ConceptRefinementRound round) {
        CompletedSource source;
        try { source = validations.requireCurrentCompletedSource(ownerId, projectId); }
        catch (BusinessException unavailable) { return false; }
        if (!round.boundTo(source)) return false;
        boolean selectionCurrent = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId)
            .filter(value -> Objects.equals(value.getId(), round.getSelectionId())
                && Objects.equals(value.getHypothesisRevision(), round.getSourceSelectionRevision()))
            .isPresent();
        if (!selectionCurrent) return false;
        boolean seedCurrent = seeds
            .findByIdAndStaleAtIsNullAndDeletedAtIsNull(round.getSourceMarketSeedSnapshotId())
            .filter(value -> Objects.equals(value.getId(), round.getSourceMarketSeedSnapshotId())
                && Objects.equals(value.getProjectId(), projectId)
                && Objects.equals(value.getPortfolioSelectionId(), round.getSelectionId())
                && "CONCEPT_PORTFOLIO_V2".equals(value.getSourceType()))
            .isPresent();
        if (!seedCurrent) return false;
        try { return bmPlans.current(projectId).revision() == round.getSourceBmPlanRevision(); }
        catch (BusinessException unavailable) { return false; }
    }

    public boolean postApplyCurrent(Long projectId, ConceptRefinementRound round) {
        if (round.getAppliedSelectionRevision() == null || round.getAppliedBmPlanRevision() == null) return false;
        boolean selectionCurrent = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId)
            .filter(value -> Objects.equals(value.getId(), round.getSelectionId())
                && Objects.equals(value.getHypothesisRevision(), round.getAppliedSelectionRevision()))
            .isPresent();
        if (!selectionCurrent) return false;
        try { return bmPlans.current(projectId).revision() == round.getAppliedBmPlanRevision(); }
        catch (BusinessException unavailable) { return false; }
    }
}
