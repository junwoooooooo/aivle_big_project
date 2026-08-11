package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MarketResearchProductInputTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketResearchInputFactory factory = new MarketResearchInputFactory(mapper);

    @Test
    void arbitrarySelectedConceptBuildsProductInputWithoutSavedLedger() {
        JsonNode input = mapper.readTree(factory.full(seed("seed-77"), selection("arbitrary-77"), "2026-08-11"));

        assertThat(input.path("conceptId").asText()).isEqualTo("arbitrary-77");
        assertThat(input.path("conceptSnapshotJson").asText()).contains("Arbitrary Selected Business");
        assertThat(input.path("marketSeedSnapshotJson").asText()).contains("finalHypotheses");
        assertThat(input.get("sourceRun")).isNull();
        assertThat(input.path("source").path("selectionRevision").asInt()).isEqualTo(4);
    }

    @Test
    void productInputContainsConfirmedHypothesesAndFinalLegalResult() {
        JsonNode input = mapper.readTree(factory.full(seed("seed-1"), selection("concept-1"), "2026-08-11"));
        JsonNode concept = mapper.readTree(input.path("conceptSnapshotJson").asText());

        assertThat(concept.path("_hypotheses_v2").path("revenueModel").path("value").asText())
            .isEqualTo("monthly subscription");
        assertThat(concept.path("_target_legal").path("legalStatus").asText()).isEqualTo("PASS");
    }

    @Test
    void productInputPassesCanonicalHasher() {
        String input = factory.full(seed("seed-2"), selection("concept-2"), "2026-08-11");
        assertThatCode(() -> new CanonicalInputHasher(mapper)
            .hash(TaskType.MARKET_RESEARCH, "1.0", "ko-KR", input))
            .doesNotThrowAnyException();
    }

    private ConceptPortfolioSelection selection(String conceptId) {
        ConceptPortfolioSelection value = mock(ConceptPortfolioSelection.class);
        when(value.getId()).thenReturn(77L);
        when(value.getRunId()).thenReturn("portfolio-run");
        when(value.getConceptId()).thenReturn(conceptId);
        when(value.getHypothesisRevision()).thenReturn(4);
        return value;
    }

    private MarketAnalysisSeedSnapshot seed(String id) {
        MarketAnalysisSeedSnapshot value = mock(MarketAnalysisSeedSnapshot.class);
        when(value.getId()).thenReturn(id);
        when(value.getSnapshotHash()).thenReturn("sha256:" + "a".repeat(64));
        when(value.getSnapshotJson()).thenReturn("""
            {"selectedConcept":{"identity":{"conceptName":"Arbitrary Selected Business","targetUsers":"small merchants"},
            "solution":{"problemScenario":"repetitive work","solutionMechanism":"automation","featureSet":["alerts"]},
            "operation":{"operatingModel":"SaaS","transactionFlow":"subscription","platformRole":"automation",
            "featureSet":["alerts"],"partnerModel":"payments","partnerRequirements":["PG"]}},
            "finalHypotheses":{"targetRegion":{"value":"Korea"},"price":{"value":"KRW 9900"},
            "revenueModel":{"value":"monthly subscription"},"channels":{"value":["online"]},
            "differentiators":{"value":["automation"]}},
            "legalResult":{"legalStatus":"PASS","safeSummary":"conditional","risks":[],"requiredActions":[]}}
            """);
        return value;
    }
}
