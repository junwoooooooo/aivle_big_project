package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.concept.application.ConceptLegalFactPatternMapper;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptError;
import com.aivle.backend.pipeline.concept.domain.ConceptCandidateV2Validator;
import com.aivle.backend.pipeline.concept.domain.ConceptGenerationStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ConceptCandidateV2ValidatorTests {
    private static final List<String> SEMANTIC_FIELDS = List.of(
        "conceptName", "conceptDefinition", "introduction", "coreValue", "targetUsers",
        "industryCategory", "researchScope",
        "targetRegion", "revenueModel", "price", "channels", "differentiators",
        "preMarketSomShareHypothesis", "preMarketSomHypothesis", "problemScenario", "solutionMechanism",
        "featureSet", "actorRoles", "platformRole",
        "operatingModel", "partnerModel", "providerRole", "sellerRole", "intermediaryRole",
        "transactionFlow", "paymentFlow", "personalDataUsage",
        "physicalActivities", "partnerRequirements", "qualificationRequirements", "advertisingClaims"
    );
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void minimalSeedAcceptsCompleteExploreCandidateAndCreatesMissingBusinessValuesAsHypotheses() {
        ObjectNode candidate = candidate(ConceptGenerationStrategy.EXPLORE, 1);
        var result = ConceptCandidateV2Validator.validate(candidate, ConceptGenerationStrategy.EXPLORE, 1,
            minimalSeed());

        assertThat(result.accepted()).isTrue();
        assertThat(semantics(candidate, "revenueModel").path("source").asText()).isEqualTo("AI_HYPOTHESIS");
        assertThat(semantics(candidate, "revenueModel").path("decision").asText()).isEqualTo("PROPOSED");
        assertThat(semantics(candidate, "targetRegion").path("source").asText()).isEqualTo("AI_HYPOTHESIS");
    }

    @Test
    void lockedRevenueModelMustBePreservedWithUserAuthority() {
        ObjectNode candidate = candidate(ConceptGenerationStrategy.REFINE, 1);
        candidate.put("revenueModel", "거래당 2,000원");
        setSemantics(candidate, "revenueModel", "USER_INPUT", "LOCKED", "ACCEPTED");
        List<Map<String, String>> seed = new ArrayList<>(minimalSeed());
        seed.add(field("revenueModel", "월 9,900원 구독", "USER_INPUT", "LOCKED"));

        var result = ConceptCandidateV2Validator.validate(candidate, ConceptGenerationStrategy.REFINE, 1, seed);

        assertThat(result.accepted()).isFalse();
        assertThat(result.error()).isEqualTo(ConceptAttemptError.LOCKED_CONSTRAINT_INVALID);
    }

    @Test
    void lockedRevenueModelPassesWhenValueAndAuthorityArePreserved() {
        ObjectNode candidate = candidate(ConceptGenerationStrategy.REFINE, 1);
        candidate.put("revenueModel", "월 9,900원 구독");
        setSemantics(candidate, "revenueModel", "USER_INPUT", "LOCKED", "ACCEPTED");
        List<Map<String, String>> seed = new ArrayList<>(minimalSeed());
        seed.add(field("revenueModel", "월 9,900원 구독", "USER_INPUT", "LOCKED"));

        var result = ConceptCandidateV2Validator.validate(candidate, ConceptGenerationStrategy.REFINE, 1, seed);

        assertThat(result.accepted()).isTrue();
    }

    @Test
    void userConfirmedLockedRegionIsPreservedAndAnOpenForeignRegionIsRejectedBeforeLegal() {
        ObjectNode locked = candidate(ConceptGenerationStrategy.REFINE, 1);
        locked.put("targetRegion", "대한민국");
        setSemantics(locked, "targetRegion", "USER_CONFIRMED", "LOCKED", "ACCEPTED");
        List<Map<String, String>> seed = new ArrayList<>(minimalSeed());
        seed.add(field("targetRegion", "대한민국", "USER_CONFIRMED", "LOCKED"));
        assertThat(ConceptCandidateV2Validator.validate(
            locked, ConceptGenerationStrategy.REFINE, 1, seed).accepted()).isTrue();

        ObjectNode foreign = candidate(ConceptGenerationStrategy.EXPLORE, 1);
        foreign.put("targetRegion", "미국 캘리포니아");
        var rejected = ConceptCandidateV2Validator.validate(
            foreign, ConceptGenerationStrategy.EXPLORE, 1, minimalSeed());
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.safeCode()).isEqualTo("LEGAL_JURISDICTION_UNSUPPORTED");
    }

    @Test
    void asIsCandidateOnePreservesTheUserOriginal() {
        ObjectNode candidate = candidate(ConceptGenerationStrategy.AS_IS, 1);
        candidate.put("conceptDefinition", "동네 가게의 남는 식재료를 이웃과 연결한다");
        candidate.put("problemScenario", "동네 가게의 남는 식재료가 매일 폐기된다");
        candidate.put("targetUsers", "남는 식재료가 있는 동네 가게");

        var result = ConceptCandidateV2Validator.validate(candidate, ConceptGenerationStrategy.AS_IS, 1,
            minimalSeed());

        assertThat(result.accepted()).isTrue();
        assertThat(candidate.path("originalCandidate").asBoolean()).isTrue();
    }

    @Test
    void preMarketSomCannotMasqueradeAsAnAcceptedAnalysisResult() {
        ObjectNode candidate = candidate(ConceptGenerationStrategy.EXPLORE, 1);
        setSemantics(candidate, "preMarketSomHypothesis", "ANALYSIS_RESULT", "REVIEWABLE", "ACCEPTED");

        var result = ConceptCandidateV2Validator.validate(candidate, ConceptGenerationStrategy.EXPLORE, 1,
            minimalSeed());

        assertThat(result.accepted()).isFalse();
        assertThat(result.safeCode()).isEqualTo("PRE_MARKET_SOM_SEMANTICS_INVALID");
    }

    @Test
    void mapsCompleteConceptFactsAndSensitiveHypothesesBeforeLegalReview() {
        ObjectNode candidate = candidate(ConceptGenerationStrategy.EXPLORE, 1);

        var result = new ConceptLegalFactPatternMapper(mapper).map(candidate);

        assertThat(result.factPattern().path("commercialRoles").path("sellerRole").path("value").asText())
            .isEqualTo("동네 가게가 식재료 판매자 역할을 맡는다");
        assertThat(result.factPattern().path("paymentFlow").path("value").get(0).asText())
            .isEqualTo("구매자가 가게에 결제");
        assertThat(result.factPattern().path("hypotheses").path("revenueModel").path("legalSensitivity").asText())
            .isEqualTo("LEGAL_SENSITIVE");
        assertThat(result.factPattern().path("hypotheses").path("revenueModel").path("source").asText())
            .isEqualTo("AI_HYPOTHESIS");
        assertThat(result.factPattern().path("jurisdiction").asText()).isEqualTo("KR");
        assertThat(result.factPattern().toString()).doesNotContain("preMarketSom");
        assertThat(result.factPatternHash()).matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void paymentAndRoleChangesProduceAnotherFactPatternHash() {
        ObjectNode original = candidate(ConceptGenerationStrategy.EXPLORE, 1);
        ObjectNode changed = original.deepCopy();
        changed.put("sellerRole", "플랫폼 운영자가 직접 판매자 역할을 맡는다");
        changed.putArray("paymentFlow").add("구매자가 플랫폼에 결제하고 플랫폼이 가게에 정산");
        ConceptLegalFactPatternMapper factPatterns = new ConceptLegalFactPatternMapper(mapper);

        assertThat(factPatterns.map(changed).factPatternHash())
            .isNotEqualTo(factPatterns.map(original).factPatternHash());
    }

    private List<Map<String, String>> minimalSeed() {
        return List.of(
            field("ideaOverview", "동네 가게의 남는 식재료를 이웃과 연결한다", "USER_INPUT", "LOCKED"),
            field("problem", "남는 식재료가 매일 폐기된다", "USER_INPUT", "LOCKED"),
            field("targetUsers", "남는 식재료가 있는 동네 가게", "USER_INPUT", "LOCKED")
        );
    }

    private Map<String, String> field(String key, String value, String source, String authority) {
        return Map.of("fieldKey", key, "value", value, "source", source, "authority", authority);
    }

    private ObjectNode candidate(ConceptGenerationStrategy strategy, int index) {
        ObjectNode value = mapper.createObjectNode();
        value.put("schemaVersion", "2.0");
        value.put("generationStrategy", strategy.name());
        value.put("candidateIndex", index);
        value.put("originalCandidate", strategy == ConceptGenerationStrategy.AS_IS && index == 1);
        value.put("conceptName", "동네 식재료 연결");
        value.put("conceptDefinition", "동네 가게의 남는 식재료를 이웃과 연결한다");
        value.put("introduction", "폐기를 줄이고 이웃의 소량 구매를 돕는다");
        value.put("coreValue", "당일 소량 거래");
        value.put("targetUsers", "남는 식재료가 있는 동네 가게");
        value.put("industryCategory", "지역 식품 유통");
        value.put("researchScope", "서울 지역 소형 식품점");
        value.put("targetRegion", "서울");
        value.put("revenueModel", "월 구독");
        value.put("price", "월 9,900원");
        value.put("channels", "지역 상인회 직접 영업");
        value.put("differentiators", "당일 남는 재고만 연결");
        ObjectNode share = value.putObject("preMarketSomShareHypothesis");
        share.put("targetSharePercent", 2.5); share.put("horizonYears", 3);
        share.put("rationale", "초기 지역 집중 가설"); share.putArray("assumptions").add("상인회 참여");
        ObjectNode som = value.putObject("preMarketSomHypothesis");
        som.put("amount", 100000000); som.put("currency", "KRW"); som.put("period", "연간");
        som.put("calculationBasis", "가설 고객 수와 구독료의 곱"); som.putArray("assumptions").add("유료 전환");
        som.put("confidence", "LOW");
        value.put("problemScenario", "남는 식재료가 매일 폐기된다");
        value.put("solutionMechanism", "마감 전 재고를 등록해 이웃에게 당일 연결한다");
        value.putArray("featureSet").add("마감 재고 등록");
        value.putArray("actorRoles").add("동네 가게").add("구매자");
        value.put("platformRole", "재고 정보와 주문 연결 중개");
        value.put("operatingModel", "가게가 재고를 등록하고 직접 인도한다");
        value.put("partnerModel", "상인회와 입점 가게 제휴");
        value.put("providerRole", "플랫폼 운영자가 주문 연결 기능을 제공한다");
        value.put("sellerRole", "동네 가게가 식재료 판매자 역할을 맡는다");
        value.put("intermediaryRole", "플랫폼은 거래 당사자가 아닌 정보 중개자 역할을 맡는다");
        value.putArray("transactionFlow").add("가게 등록").add("구매자 주문");
        value.putArray("paymentFlow").add("구매자가 가게에 결제");
        value.putArray("personalDataUsage").add("주문 연락처 처리");
        value.putArray("physicalActivities").add("가게 현장 수령");
        value.putArray("partnerRequirements").add("식품 판매 사업자");
        value.putArray("qualificationRequirements").add("관련 영업 신고");
        value.putArray("advertisingClaims").add("당일 등록 재고");
        value.putArray("constraintCompliance");
        ArrayNode semantics = value.putArray("valueSemantics");
        for (String field : SEMANTIC_FIELDS) {
            ObjectNode item = semantics.addObject();
            item.put("fieldKey", field);
            if (field.startsWith("preMarketSom") || Set.of(
                    "targetRegion", "revenueModel", "price", "channels", "differentiators").contains(field)) {
                item.put("source", "AI_HYPOTHESIS"); item.put("authority", "OPEN"); item.put("decision", "PROPOSED");
            } else {
                item.put("source", "CONCEPT_GENERATED"); item.put("authority", "OPEN"); item.put("decision", "PROPOSED");
            }
        }
        if (strategy == ConceptGenerationStrategy.AS_IS && index == 1) {
            setSemantics(value, "conceptDefinition", "USER_INPUT", "LOCKED", "ACCEPTED");
            setSemantics(value, "problemScenario", "USER_INPUT", "LOCKED", "ACCEPTED");
            setSemantics(value, "targetUsers", "USER_INPUT", "LOCKED", "ACCEPTED");
        }
        return value;
    }

    private ObjectNode semantics(ObjectNode candidate, String field) {
        for (var value : candidate.path("valueSemantics")) {
            if (field.equals(value.path("fieldKey").asText())) return (ObjectNode) value;
        }
        throw new IllegalArgumentException("missing semantics");
    }

    private void setSemantics(ObjectNode candidate, String field, String source, String authority, String decision) {
        ObjectNode value = semantics(candidate, field);
        value.put("source", source); value.put("authority", authority); value.put("decision", decision);
    }
}
