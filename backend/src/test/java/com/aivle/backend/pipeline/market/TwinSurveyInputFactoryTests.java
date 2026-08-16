package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TwinSurveyInputFactoryTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void canonicalInputContainsExactCurrentConceptAndNoMarketInterviewResult() {
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        when(seed.getId()).thenReturn("seed-1");
        when(seed.getSnapshotHash()).thenReturn("sha256:" + "a".repeat(64));
        when(seed.getSnapshotJson()).thenReturn("""
            {"contract":"market-analysis-seed-snapshot-v1","schemaVersion":"2.0",
             "selectedConcept":{"identity":{"name":"현재 사업안"}},
             "finalHypotheses":{"target":{"value":"운영자"}}}
            """);
        when(selection.getId()).thenReturn(31L);
        when(selection.getHypothesisRevision()).thenReturn(4);
        var source = new CurrentConceptSourceResolver.Source(selection, seed,
            new BmPlanPreparationService.PlanView(mapper.createObjectNode().put("customer_relationship", "상담"),
                mapper.createObjectNode(), 3));
        var pairs = mapper.createArrayNode(); pairs.addObject().put("pairId", "P1");

        var value = mapper.readTree(new TwinSurveyInputFactory(mapper)
            .build(source, "상점에서 두 안을 비교합니다.", pairs, 100));

        assertThat(value.path("source").path("marketSeedSnapshotId").asText()).isEqualTo("seed-1");
        assertThat(value.path("source").path("selectionRevision").asInt()).isEqualTo(4);
        assertThat(value.path("source").path("bmPlanRevision").asInt()).isEqualTo(3);
        assertThat(value.path("concept").path("selectedConcept").path("identity").path("name").asText())
            .isEqualTo("현재 사업안");
        assertThat(value.has("marketInterview")).isFalse();
    }
}
