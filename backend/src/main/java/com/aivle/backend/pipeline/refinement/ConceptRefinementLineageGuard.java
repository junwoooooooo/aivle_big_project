package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
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
    private final MarketResearchVersionRepository marketVersions;

    public ConceptRefinementLineageGuard(BusinessValidationCoordinator validations,
            ConceptPortfolioSelectionRepository selections,
            MarketAnalysisSeedSnapshotRepository seeds, BmPlanPreparationService bmPlans,
            MarketResearchVersionRepository marketVersions) {
        this.validations = validations; this.selections = selections;
        this.seeds = seeds; this.bmPlans = bmPlans;
        this.marketVersions = marketVersions;
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
        if (round.getState() == ConceptRefinementRound.State.FINALIZED
                && (round.getFinalMarketSeedSnapshotId() == null || seeds
                    .findByIdAndStaleAtIsNullAndDeletedAtIsNull(round.getFinalMarketSeedSnapshotId()).isEmpty()))
            return false;
        try { return bmPlans.current(projectId).revision() == round.getAppliedBmPlanRevision(); }
        catch (BusinessException unavailable) { return false; }
    }

    /** Validates the editable baseline without requiring the original validation session to remain current. */
    public boolean proposalBaselineCurrent(Long ownerId, Long projectId, ConceptRefinementRound round) {
        if (round.getRoundNumber() == 1) return preApplyCurrent(ownerId, projectId, round);
        boolean selectionCurrent = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId)
            .filter(value -> Objects.equals(value.getId(), round.getSelectionId())
                && value.getHypothesisRevision() == round.baselineSelectionRevision())
            .isPresent();
        if (!selectionCurrent) return false;
        boolean exactSeedExists = seeds.findByIdAndDeletedAtIsNull(round.getSourceMarketSeedSnapshotId())
            .filter(value -> Objects.equals(value.getProjectId(), projectId)
                && Objects.equals(value.getPortfolioSelectionId(), round.getSelectionId())
                && "CONCEPT_PORTFOLIO_V2".equals(value.getSourceType()))
            .isPresent();
        if (!exactSeedExists) return false;
        boolean evidenceExists = marketVersions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(
                round.getSourceMarketVersionId(), projectId, MarketResearchRun.Kind.FULL).isPresent()
            && marketVersions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(
                round.getSourceBmVersionId(), projectId, MarketResearchRun.Kind.BM).isPresent();
        if (!evidenceExists) return false;
        try { return bmPlans.current(projectId).revision() == round.baselineBmPlanRevision(); }
        catch (BusinessException unavailable) { return false; }
    }
}
