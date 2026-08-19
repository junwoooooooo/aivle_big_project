package com.aivle.backend.pipeline.marketinterview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.refinement.ConceptRefinementFinal;
import com.aivle.backend.pipeline.refinement.ConceptRefinementFinalRepository;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRound;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRoundRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketInterviewSourceResolverTests {
    private final ConceptRefinementFinalRepository finals=mock(ConceptRefinementFinalRepository.class);
    private final ConceptRefinementRoundRepository rounds=mock(ConceptRefinementRoundRepository.class);
    private final ConceptPortfolioSelectionRepository selections=mock(ConceptPortfolioSelectionRepository.class);
    private final MarketAnalysisSeedSnapshotRepository seeds=mock(MarketAnalysisSeedSnapshotRepository.class);
    private final BmPlanPreparationService bmPlans=mock(BmPlanPreparationService.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final MarketInterviewSourceResolver resolver=new MarketInterviewSourceResolver(
        finals,rounds,selections,seeds,bmPlans,mapper);
    private final ConceptRefinementFinal fin=mock(ConceptRefinementFinal.class);
    private final ConceptRefinementRound round=mock(ConceptRefinementRound.class);
    private final ConceptPortfolioSelection selection=mock(ConceptPortfolioSelection.class);
    private final MarketAnalysisSeedSnapshot seed=mock(MarketAnalysisSeedSnapshot.class);

    @BeforeEach void currentFixture(){
        when(finals.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.of(fin));
        when(fin.getId()).thenReturn(17L);when(fin.getRoundId()).thenReturn(9L);when(fin.getSelectionId()).thenReturn(31L);
        when(fin.getSourceBusinessValidationSessionId()).thenReturn("session-1");
        when(fin.getFinalMarketSeedSnapshotId()).thenReturn("seed-final");
        when(fin.getFinalSelectionRevision()).thenReturn(5);when(fin.getFinalBmPlanRevision()).thenReturn(4);
        when(fin.getFinalJson()).thenReturn("""
          {"contract":"concept-refinement-final-v1","final":{"marketSeedSnapshotId":"seed-final","selectionRevision":5,"bmPlanRevision":4},
           "selectedConcept":{"identity":{"conceptName":"예약 도우미"}},"finalHypotheses":{}}
          """);
        when(rounds.findById(9L)).thenReturn(Optional.of(round));when(round.isDeleted()).thenReturn(false);
        when(round.getState()).thenReturn(ConceptRefinementRound.State.FINALIZED);when(round.getFinalId()).thenReturn(17L);
        when(round.getFinalMarketSeedSnapshotId()).thenReturn("seed-final");when(round.getBusinessValidationSessionId()).thenReturn("session-1");
        when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(selection.getId()).thenReturn(31L);when(selection.getHypothesisRevision()).thenReturn(5);
        when(seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull("seed-final")).thenReturn(Optional.of(seed));
        when(seed.getProjectId()).thenReturn(41L);when(seed.getPortfolioSelectionId()).thenReturn(31L);
        when(bmPlans.current(41L)).thenReturn(new BmPlanPreparationService.PlanView(
            mapper.createObjectNode(),mapper.createObjectNode(),4));
    }

    @Test void currentFinalizedSourceIsReady(){
        assertThat(resolver.currentOrNull(41L)).isNotNull();
        assertThat(resolver.require(41L).refinementFinal().getId()).isEqualTo(17L);
    }

    @Test void noFinalizedRefinementIsRejected(){
        when(finals.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.empty());
        assertThat(resolver.currentOrNull(41L)).isNull();
        assertThatThrownBy(()->resolver.require(41L)).isInstanceOf(BusinessException.class);
    }

    @Test void selectionOrBmRevisionChangeMakesSourceStale(){
        when(selection.getHypothesisRevision()).thenReturn(6);
        assertThat(resolver.currentOrNull(41L)).isNull();
        when(selection.getHypothesisRevision()).thenReturn(5);
        when(bmPlans.current(41L)).thenReturn(new BmPlanPreparationService.PlanView(
            mapper.createObjectNode(),mapper.createObjectNode(),5));
        assertThat(resolver.currentOrNull(41L)).isNull();
    }
}
