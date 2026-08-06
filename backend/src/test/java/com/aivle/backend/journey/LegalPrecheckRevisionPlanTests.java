package com.aivle.backend.journey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

class LegalPrecheckRevisionPlanTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LegalPrecheckService service = new LegalPrecheckService(
        null, null, null, null, null, null, null, null, null, null, mapper
    );

    @Test
    void groupsProhibitionEvidenceByCategoryIntoOneRevisionPlan() {
        ArrayNode evidence = mapper.createArrayNode();
        evidence.add(evidence("EVD-001", "personal-data", "PRIVACY_AND_DATA", "개인정보 처리를 금지합니다."));
        evidence.add(evidence("EVD-002", "personal-data", "PRIVACY_AND_DATA", "동의 없는 제공을 금지합니다."));
        evidence.add(evidence("EVD-003", "consumer", "CONSUMER_PROTECTION", "기만 표시를 금지합니다."));
        ArrayNode routes = mapper.createArrayNode();
        routes.add(route("personal-data", "위치정보와 이용 기록을 처리한다"));
        routes.add(route("consumer", "자동 절전 효과를 안내한다"));

        ArrayNode plans = service.revisionSuggestions(evidence, routes, Set.of());

        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).path("category").asText()).isEqualTo("PRIVACY_AND_DATA");
        assertThat(plans.get(0).path("evidenceIds")).hasSize(2);
        assertThat(plans.get(0).path("targetField").asText()).isEqualTo("legal.privacy_and_data");
    }

    @Test
    void doesNotProposeAnAlreadyAcceptedLegalCategoryAgain() {
        ArrayNode evidence = mapper.createArrayNode();
        evidence.add(evidence("EVD-001", "personal-data", "PRIVACY_AND_DATA", "개인정보 처리를 금지합니다."));
        evidence.add(evidence("EVD-002", "consumer", "CONSUMER_PROTECTION", "기만 표시를 금지합니다."));
        ArrayNode routes = mapper.createArrayNode();
        routes.add(route("personal-data", "위치정보를 처리한다"));
        routes.add(route("consumer", "절전 효과를 안내한다"));

        ArrayNode plans = service.revisionSuggestions(
            evidence, routes, Set.of("PRIVACY_AND_DATA")
        );

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).path("category").asText()).isEqualTo("CONSUMER_PROTECTION");
    }

    @Test
    void partialOfficialSourcesAllowConditionalConceptProgress() {
        ArrayNode evidence = mapper.createArrayNode();
        evidence.add(evidence("EVD-001", "personal-data", "PRIVACY_AND_DATA",
            "개인정보 처리방침을 공개해야 합니다."));
        var empty = mapper.createArrayNode();
        var guardrail = new LegalPrecheckService.GuardrailDraft(
            mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode(),
            mapper.createArrayNode(), mapper.createArrayNode());

        var status = service.decide("SOURCE_PARTIAL", empty, guardrail, evidence, Set.of());

        assertThat(status).isEqualTo(LegalPrecheckVersion.Status.PASS_WITH_CONDITIONS);
    }

    @Test
    void registryGapAndEmptyEvidenceRemainBlocked() {
        ArrayNode evidence = mapper.createArrayNode();
        evidence.add(evidence("EVD-001", "personal-data", "PRIVACY_AND_DATA",
            "개인정보 처리방침을 공개해야 합니다."));
        var empty = mapper.createArrayNode();
        var guardrail = new LegalPrecheckService.GuardrailDraft(
            mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode(),
            mapper.createArrayNode(), mapper.createArrayNode());

        assertThat(service.decide("REGISTRY_GAP", empty, guardrail, evidence, Set.of()))
            .isEqualTo(LegalPrecheckVersion.Status.EXPERT_REVIEW_REQUIRED);
        assertThat(service.decide("SOURCE_PARTIAL", empty, guardrail,
            mapper.createArrayNode(), Set.of()))
            .isEqualTo(LegalPrecheckVersion.Status.EXPERT_REVIEW_REQUIRED);
    }

    private tools.jackson.databind.node.ObjectNode evidence(
            String id, String routeId, String category, String summary) {
        var value = mapper.createObjectNode();
        value.put("evidenceId", id);
        value.put("routeId", routeId);
        value.put("category", category);
        value.put("role", "REQUIREMENT");
        value.put("plainSummary", summary);
        value.put("whyRelevant", "현재 Origin의 사업 방식에 관련됩니다.");
        return value;
    }

    private tools.jackson.databind.node.ObjectNode route(String routeId, String quote) {
        var value = mapper.createObjectNode();
        value.put("routeId", routeId);
        value.putArray("evidenceQuotes").add(quote);
        return value;
    }
}
