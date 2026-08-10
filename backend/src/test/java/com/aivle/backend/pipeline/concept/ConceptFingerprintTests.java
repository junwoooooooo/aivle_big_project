package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.concept.domain.ConceptFingerprint;
import com.aivle.backend.pipeline.concept.domain.ConceptSemanticDistinctnessResult;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ConceptFingerprintTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void renamedOnlyCandidateIsDuplicateBecauseNameIsNotFingerprintInput() {
        JsonNode first = candidate("동네 재고 연결", "남은 식재료를 당일 연결한다");
        JsonNode renamed = candidate("마감 식재료 브릿지", "남은 식재료를 당일 연결한다");

        assertThat(ConceptFingerprint.duplicates(first, renamed)).isTrue();
    }

    @Test
    void semanticallySameStructureWithSmallWordingChangesIsDuplicate() {
        JsonNode first = candidate("동네 재고 연결", "남은 식재료를 당일 연결한다");
        JsonNode paraphrased = candidate("지역 재고 연결", "남은 식재료를 당일 바로 연결한다");

        assertThat(ConceptFingerprint.duplicates(first, paraphrased)).isTrue();
    }

    @Test
    void structuredSummaryExcludesNameAndIncludesCommercialOperationalAndRoles() {
        var summary = ConceptFingerprint.businessSummary(candidate("이름", "개인 참가자를 자동 배정"));
        assertThat(summary).doesNotContainKey("conceptName");
        assertThat(summary).containsKeys("targetUsers", "solutionMechanism", "revenueModel", "channels",
            "operatingModel", "transactionFlow", "providerRole", "sellerRole", "intermediaryRole");
    }

    @Test
    void sharedFixtureAndBackendProducerHaveExactlyTheSame21FieldsAndTypes() throws Exception {
        JsonNode fixture = mapper.readTree(Files.readString(
            Path.of("../contracts/concept/business-fingerprint-v1.json")));

        Map<String, Object> summary = ConceptFingerprint.businessSummary(fixture);

        assertThat(ConceptFingerprint.businessFieldNames()).hasSize(21);
        assertThat(summary.keySet()).containsExactlyInAnyOrderElementsOf(ConceptFingerprint.businessFieldNames());
        assertThat(summary.keySet()).containsExactlyInAnyOrderElementsOf(
            mapper.convertValue(fixture, Map.class).keySet());
        for (String field : List.of("transactionFlow", "featureSet", "actorRoles", "paymentFlow",
                "personalDataUsage", "physicalActivities", "partnerRequirements", "qualificationRequirements")) {
            assertThat(summary.get(field)).isInstanceOf(List.class);
        }
        for (String field : ConceptFingerprint.businessFieldNames()) {
            if (!List.of("transactionFlow", "featureSet", "actorRoles", "paymentFlow",
                    "personalDataUsage", "physicalActivities", "partnerRequirements", "qualificationRequirements").contains(field)) {
                assertThat(summary.get(field)).isInstanceOf(String.class);
            }
        }
    }

    @Test
    void actualCandidateInputFixtureUsesBackendSummaryForAllThreeNonEmptyHistoryFamilies() throws Exception {
        JsonNode input = mapper.readTree(Files.readString(
            Path.of("../contracts/concept/concept-candidate-input-v1.json")));
        Map<String, Object> expected = ConceptFingerprint.businessSummary(
            input.path("rejectedConceptFingerprints").get(0));

        for (String family : List.of("acceptedConceptFingerprints", "rejectedConceptFingerprints",
                "currentSlotPreviousFingerprints")) {
            assertThat(mapper.convertValue(input.path(family).get(0), Map.class)).isEqualTo(expected);
        }
    }

    @Test
    void semanticJudgeResultHasNoRawReasoningAndAcceptsOnlyStrictDecisionSchema() {
        JsonNode result = mapper.readTree("""
            {"decision":"DUPLICATE","overlappingDimensions":["revenueModel","solutionMechanism"],
             "materiallyDifferentDimensions":[],"safeSummary":"표현만 다르고 사업 구조가 같습니다."}
            """);
        assertThat(ConceptSemanticDistinctnessResult.validate(result))
            .isEqualTo(ConceptSemanticDistinctnessResult.Decision.DUPLICATE);
    }

    @Test
    void sameCoreSeedWithDifferentRevenueMechanicsIsDistinctForRevenueFocus() {
        JsonNode first = focused("월 정액 구독", "월 39000원", "고객이 플랫폼에 월 선결제",
            "가게 직접 배송", "상인회", "고객 주소 사용", "냉장 배송");
        JsonNode second = focused("건별 중개 수수료", "주문당 4900원", "고객 결제 후 판매자 정산",
            "파트너 공동 배송", "물류사와 식품점", "주문번호만 사용", "파트너 픽업");
        assertThat(ConceptFingerprint.classify(second, first, VariationFocus.REVENUE_AND_PRICING))
            .isEqualTo(ConceptFingerprint.Classification.DISTINCT);
    }

    @Test
    void sameIdeaWithDifferentPartnerAndTransactionStructureIsDistinctForOperatingFocus() {
        JsonNode first = focused("월 구독", "39000원", "플랫폼 선결제", "가게 직접 배송",
            "상인회", "고객 주소 사용", "냉장 배송");
        JsonNode second = focused("월 구독", "39000원", "파트너가 판매하고 플랫폼은 예약만 중개",
            "지역 물류사가 수거 배송", "반찬가게와 냉장 물류사", "파트너가 주소 관리", "공동 물류");
        assertThat(ConceptFingerprint.classify(second, first, VariationFocus.OPERATING_MODEL_AND_PARTNERS))
            .isEqualTo(ConceptFingerprint.Classification.DISTINCT);
    }

    @Test
    void lowRiskFocusAcceptsMateriallyReducedDataLogisticsAndPartnerDependency() {
        JsonNode dependent = focused("월 구독", "39000원", "플랫폼 선결제", "직접 냉장 배송",
            "다수 물류사 필수", "건강정보와 주소 저장", "직접 소분과 냉장 물류");
        JsonNode lowRisk = focused("추천 이용권", "월 4900원", "디지털 이용권 결제", "배송 없음",
            "파트너 불필요", "기기 내 알레르기 정보 처리", "신체 활동 없음");
        assertThat(ConceptFingerprint.classify(lowRisk, dependent, VariationFocus.LOW_RISK_FAST_EXECUTION))
            .isEqualTo(ConceptFingerprint.Classification.DISTINCT);
    }

    @Test
    void customerExperienceAndChannelFocusAcceptTheirMateriallyDifferentAxes() {
        JsonNode first = focused("월 구독", "39000원", "플랫폼 선결제", "가게 직접 배송",
            "상인회", "고객 주소 사용", "냉장 배송");
        JsonNode customer = first.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) customer)
            .put("solutionMechanism", "사진 한 장으로 개인화 식단을 즉시 구성한다")
            .put("featureSet", "원터치 교체와 알레르기 대안 추천");
        assertThat(ConceptFingerprint.classify(customer, first, VariationFocus.CUSTOMER_EXPERIENCE))
            .isEqualTo(ConceptFingerprint.Classification.DISTINCT);

        JsonNode channel = first.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) channel)
            .put("channels", "기업 복지몰과 오피스 공동구매")
            .put("platformRole", "복지몰 주문을 지역 공급자에게 배분");
        assertThat(ConceptFingerprint.classify(channel, first, VariationFocus.CHANNEL_AND_SCALE))
            .isEqualTo(ConceptFingerprint.Classification.DISTINCT);
    }

    @Test
    void renamedBusinessCloneRemainsDuplicateForEveryFocus() {
        JsonNode first = focused("월 구독", "39000원", "플랫폼 선결제", "가게 직접 배송",
            "상인회", "고객 주소 사용", "냉장 배송");
        JsonNode renamed = first.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) renamed).put("conceptName", "새 이름");
        for (VariationFocus focus : VariationFocus.values()) {
            assertThat(ConceptFingerprint.classify(renamed, first, focus))
                .isEqualTo(ConceptFingerprint.Classification.DUPLICATE);
        }
    }

    private JsonNode focused(String revenue, String price, String payment, String operation,
            String partner, String data, String physical) {
        var node = (tools.jackson.databind.node.ObjectNode) candidate("반찬 구독", "취향별 반찬을 소분 추천한다");
        node.put("revenueModel", revenue);
        node.put("price", price);
        node.put("paymentFlow", payment);
        node.put("operatingModel", operation);
        node.put("partnerModel", partner);
        node.put("actorRoles", operation + " 역할");
        node.put("transactionFlow", payment + " 후 " + operation);
        node.put("personalDataUsage", data);
        node.put("physicalActivities", physical);
        node.put("partnerRequirements", partner);
        node.put("qualificationRequirements", partner + " 자격");
        return node;
    }

    private JsonNode candidate(String name, String mechanism) {
        return mapper.readTree("""
            {"conceptName":"%s","targetUsers":"동네 소형 식품점","problemScenario":"마감 재고가 매일 폐기된다",
             "coreValue":"당일 소량 거래","solutionMechanism":"%s","revenueModel":"월 구독",
             "channels":"상인회 직접 영업","platformRole":"재고와 주문 연결 중개",
             "operatingModel":"가게가 등록하고 직접 인도","partnerModel":"상인회 제휴"}
            """.formatted(name, mechanism));
    }
}
