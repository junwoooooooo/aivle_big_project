package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.aivle.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class BmPlanPreparationServiceTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final BmPlanPreparationService service =
        new BmPlanPreparationService(mock(BmPlanPreparationRepository.class), MAPPER);

    @Test
    void planKeepsOnlyFourSupportedNonEmptyInputs() {
        ObjectNode out = service.normalizePlan(json("""
            {"key_activities":["  reservations  "],"key_resources":[],
             "key_partners":["payment provider"],"customer_relationship":" ",
             "revenue_model":"subscription"}
            """));

        assertThat(out.propertyNames()).containsExactly("key_activities", "key_partners");
        assertThat(out.path("key_activities").path(0).asText()).isEqualTo("reservations");
    }

    @Test
    void constraintsRequireNonNegativeIntegers() {
        assertThatThrownBy(() -> service.normalizeConstraints(json("{\"budget_krw\":5000000.5}")))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.normalizeConstraints(json("{\"team\":-1}")))
            .isInstanceOf(BusinessException.class);

        ObjectNode out = service.normalizeConstraints(json("{\"budget_krw\":5000000,\"months\":10,\"team\":2}"));
        assertThat(out.path("budget_krw").asLong()).isEqualTo(5_000_000L);
        assertThat(out.path("months").asInt()).isEqualTo(10);
        assertThat(out.path("team").asInt()).isEqualTo(2);
    }

    @Test
    void missingOptionalValuesRemainEmpty() {
        assertThat(service.normalizeConstraints(json("{\"months\":null}"))).isEmpty();
        assertThat(service.normalizeConstraints(null)).isEmpty();
        assertThat(service.normalizePlan(null)).isEmpty();
    }

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }
}
