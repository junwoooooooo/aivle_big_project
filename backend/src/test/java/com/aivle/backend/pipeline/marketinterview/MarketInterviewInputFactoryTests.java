package com.aivle.backend.pipeline.marketinterview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.refinement.ConceptRefinementFinal;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRound;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.HashSet;

class MarketInterviewInputFactoryTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketInterviewInputFactory factory = new MarketInterviewInputFactory(mapper);

    @ParameterizedTest
    @ValueSource(ints = {20, 40, 80})
    void canonicalProfileBankSampleSizesAreWrittenIntoV2Input(int sampleSize) {
        var source = source();
        var input = mapper.readTree(factory.build(source, factory.board(source), sampleSize));
        assertThat(input.path("contract").asText()).isEqualTo("market-interview-input-v2");
        assertThat(input.path("schemaVersion").asText()).isEqualTo("2.0");
        assertThat(input.path("sampleSize").asInt()).isEqualTo(sampleSize);
        assertThat(input.path("source").path("selectionRevision").asInt()).isEqualTo(4);
        assertThat(input.path("source").path("bmPlanRevision").asInt()).isEqualTo(3);
        assertThat(input.path("targetingContext").path("customerUnit").asText()).isEqualTo("ORGANIZATION");
        assertThat(input.path("targetingContext").path("marketSeries").asText()).isEqualTo("A");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 5, 30, 100})
    void unsupportedSampleSizeIsRejected(int sampleSize) {
        var source = source();
        assertThatThrownBy(() -> factory.build(source, factory.board(source), sampleSize))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizedBoardHasExactlySixFieldsAndPriceTamperingIsRejected() {
        var source = source(); var board = factory.board(source);
        var fields = new HashSet<String>(); board.propertyNames().forEach(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(
            "conceptName", "targetUsers", "problemScenario", "featureSet", "differentiators", "priceKrw");
        assertThat(board.path("priceKrw").asLong()).isEqualTo(9900);
        var tampered = board.deepCopy(); tampered.put("priceKrw", 8900);
        assertThatThrownBy(() -> factory.build(source, tampered, 20)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("최종 확정 가설");
    }

    @Test
    void editableStimulusDoesNotMutateFinalizedCanonicalConcept() {
        var source = source(); var board = factory.board(source); board.put("targetUsers", "서울의 독립 매장 운영자");
        var input = mapper.readTree(factory.build(source, board, 20));
        assertThat(input.path("conceptBoard").path("targetUsers").asText()).isEqualTo("서울의 독립 매장 운영자");
        assertThat(input.path("selectedConcept").path("identity").path("targetUsers").asText())
            .isEqualTo("서울 매장 운영 업체");
        assertThat(source.finalDocument().path("selectedConcept").path("identity").path("targetUsers").asText())
            .isEqualTo("서울 매장 운영 업체");
    }

    private MarketAnalysisSeedSnapshot seed() {
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        when(seed.getId()).thenReturn("seed-1");
        when(seed.getSnapshotHash()).thenReturn("sha256:" + "a".repeat(64));
        when(seed.getSnapshotJson()).thenReturn("""
            {"contract":"market-analysis-seed-snapshot-v1","schemaVersion":"2.0",
             "selectedConcept":{"identity":{"conceptName":"예약 도우미","targetUsers":"서울 매장 운영 업체"}},
             "finalHypotheses":{}}
            """);
        return seed;
    }

    private ConceptPortfolioSelection selection() {
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        when(selection.getId()).thenReturn(31L);
        when(selection.getHypothesisRevision()).thenReturn(4);
        return selection;
    }

    private MarketInterviewSourceResolver.Source source() {
        ConceptRefinementFinal fin = mock(ConceptRefinementFinal.class);
        ConceptRefinementRound round = mock(ConceptRefinementRound.class);
        when(fin.getId()).thenReturn(17L);
        var document = mapper.readTree("""
            {"contract":"concept-refinement-final-v1","selectedConcept":{
              "identity":{"conceptName":"예약 도우미","targetUsers":"서울 매장 운영 업체"},
              "solution":{"problemScenario":"예약 누락","featureSet":["예약 확인"]}},
             "finalHypotheses":{"differentiators":{"value":"누락 방지"},"price":{"value":9900}}}
            """);
        return new MarketInterviewSourceResolver.Source(fin, round, seed(), selection(),
            new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 3), document);
    }
}
