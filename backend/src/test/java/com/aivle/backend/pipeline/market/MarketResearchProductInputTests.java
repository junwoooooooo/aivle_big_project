package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import java.nio.charset.StandardCharsets;
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

        assertThat(concept.path("_hypotheses_v2").path("6_수익_가격").path("수익_방식").asText())
            .isEqualTo("monthly subscription");
        assertThat(concept.path("_target_legal").path("legalStatus").asText()).isEqualTo("PASS");
    }

    @Test
    void donorConceptPreservesEngineShapeAndDoesNotInjectConfirmedHypothesesIntoCollection() {
        JsonNode input = mapper.readTree(factory.full(seed("seed-contract"), selection("concept-contract"), "2026-08-11"));
        JsonNode concept = mapper.readTree(input.path("conceptSnapshotJson").asText());

        var plain = new java.util.ArrayList<String>();
        for (String key : concept.propertyNames()) if (!key.startsWith("_")) plain.add(key);
        assertThat(plain).containsExactlyInAnyOrder("concept_id", "name", "problem", "target", "solution",
            "region", "hypotheses", "price_hypothesis_krw", "constraint");
        assertThat(concept.path("hypotheses")).isEmpty();
        assertThat(concept.path("_계열").path("계열").asText()).isEqualTo("C");
        assertThat(concept.path("_다듬기5").path("4_업종_분류").has("코드")).isFalse();
        // 90 → 500 (2026-08-16 병합). 90 이면 _collect 가 83 을 태우고 **절 체인이 통째로
        // 건너뛰어져** judgment·prescriptions·synthesis 가 전부 null 로 나온다.
        assertThat(input.path("llmBudget").asInt()).isEqualTo(500);
    }

    @Test
    void ambiguousScaledPriceIsNotInventedAndConstraintsAllowOnlyKnownIntegers() {
        MarketAnalysisSeedSnapshot snapshot = seed("seed-price");
        String changed = snapshot.getSnapshotJson().replace("KRW 9900", "월 3만원 수준");
        when(snapshot.getSnapshotJson()).thenReturn(changed);
        var constraints = mapper.readTree("{\"budget_krw\":50000000,\"months\":10,\"team\":2,\"unknown\":5}");
        JsonNode input = mapper.readTree(factory.full(snapshot, selection("concept-price"), "2026-08-11", null, constraints));
        JsonNode concept = mapper.readTree(input.path("conceptSnapshotJson").asText());

        assertThat(concept.path("price_hypothesis_krw").isNull()).isTrue();
        assertThat(concept.path("constraint").propertyNames())
            .containsExactlyInAnyOrder("budget_krw", "months", "team");
    }

    @Test
    void missingConceptNameFailsClosed() {
        MarketAnalysisSeedSnapshot snapshot = seed("seed-missing-name");
        String original = snapshot.getSnapshotJson();
        when(snapshot.getSnapshotJson()).thenReturn(original.replace("Arbitrary Selected Business", ""));

        assertThatThrownBy(() -> factory.full(snapshot, selection("concept-missing"), "2026-08-11"))
            .isInstanceOf(com.aivle.backend.common.exception.BusinessException.class);
    }

    @Test
    void productInputPassesCanonicalHasher() {
        String input = factory.full(seed("seed-2"), selection("concept-2"), "2026-08-11");
        assertThat(input.getBytes(StandardCharsets.UTF_8).length)
            .isPositive()
            .isLessThanOrEqualTo(2 * 1024 * 1024);
        assertThatCode(() -> new CanonicalInputHasher(mapper)
            .hash(TaskType.MARKET_RESEARCH, "1.0", "ko-KR", input))
            .doesNotThrowAnyException();
    }

    @Test
    void competitorSeedsRideInsideTheImmutableConceptSnapshot() {
        var block = mapper.createObjectNode();
        block.putArray("seeds").addObject().put("이름", "공비서")
            .put("왜", "노쇼 방지 경쟁").put("운영사", "공비서 주식회사");

        JsonNode input = mapper.readTree(factory.full(
            seed("seed-competitor"), selection("concept-competitor"), "2026-08-12", block));
        JsonNode concept = mapper.readTree(input.path("conceptSnapshotJson").asText());

        assertThat(concept.path("_경쟁_씨앗").path("seeds").path(0).path("이름").asText())
            .isEqualTo("공비서");
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
            {"selectedConcept":{"identity":{"conceptName":"Arbitrary Selected Business","coreValue":"one screen","targetUsers":"small merchants","industryCategory":"retail software"},
            "solution":{"problemScenario":"repetitive work","solutionMechanism":"automation","featureSet":["alerts"]},
            "operation":{"operatingModel":"SaaS","transactionFlow":"subscription","platformRole":"automation",
            "featureSet":["alerts"],"partnerModel":"payments","partnerRequirements":["PG"]}},
            "finalHypotheses":{"targetRegion":{"value":"Korea"},"price":{"value":"KRW 9900"},
            "revenueModel":{"value":"monthly subscription"},"channels":{"value":["online"]},
            "differentiators":{"value":["automation"]},"preMarketSomShare":{"value":{"targetSharePercent":0.5,"horizonYears":3}}},
            "legalResult":{"legalStatus":"PASS","safeSummary":"conditional","risks":[],"requiredActions":[]}}
            """);
        return value;
    }
}
