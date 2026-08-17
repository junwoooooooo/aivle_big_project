package com.aivle.backend.pipeline.marketinterview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class MarketInterviewInputFactoryTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketInterviewInputFactory factory = new MarketInterviewInputFactory(mapper);

    @ParameterizedTest
    @ValueSource(ints = {20, 40, 80})
    void canonicalProfileBankSampleSizesAreWrittenIntoV2Input(int sampleSize) {
        var input = mapper.readTree(factory.build(seed(), selection(),
            new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 3), sampleSize));
        assertThat(input.path("contract").asText()).isEqualTo("market-interview-input-v2");
        assertThat(input.path("schemaVersion").asText()).isEqualTo("2.0");
        assertThat(input.path("sampleSize").asInt()).isEqualTo(sampleSize);
        assertThat(input.path("source").path("selectionRevision").asInt()).isEqualTo(4);
        assertThat(input.path("source").path("bmPlanRevision").asInt()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 5, 30, 100})
    void unsupportedSampleSizeIsRejected(int sampleSize) {
        assertThatThrownBy(() -> factory.build(seed(), selection(),
            new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 3), sampleSize))
            .isInstanceOf(BusinessException.class);
    }

    private MarketAnalysisSeedSnapshot seed() {
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        when(seed.getId()).thenReturn("seed-1");
        when(seed.getSnapshotHash()).thenReturn("sha256:" + "a".repeat(64));
        when(seed.getSnapshotJson()).thenReturn("""
            {"contract":"market-analysis-seed-snapshot-v1","schemaVersion":"2.0",
             "selectedConcept":{"identity":{"name":"예약 도우미","targetUsers":"서울 매장"}},
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
}
