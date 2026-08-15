package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.conceptportfolio.application.EffectiveAffectedFieldResolver;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class EffectiveAffectedFieldResolverTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EffectiveAffectedFieldResolver resolver = new EffectiveAffectedFieldResolver(mapper);

    @Test
    void preservesExplicitAllowedFieldAndFiltersUnknownField() {
        assertThat(resolve("{\"affectedFields\":[\"paymentFlow\",\"emailAddress\"]}"))
            .containsExactly("paymentFlow");
    }

    @Test
    void mapsHighConfidencePersonalDataAndPaymentQuestions() {
        assertThat(resolve("{\"affectedFields\":[],\"question\":\"personal data collected\"}"))
            .containsExactly("personalDataUsage");
        assertThat(resolve("{\"affectedFields\":[],\"question\":\"payment methods accepted\"}"))
            .containsExactly("paymentFlow");
        assertThat(resolve("{\"affectedFields\":[],\"question\":\"What specific personal data will be collected and how will it be used? What payment methods will be accepted and how will they be processed?\"}"))
            .containsExactly("personalDataUsage", "paymentFlow");
    }

    @Test
    void leavesAmbiguousTextUnresolved() {
        assertThat(resolve("{\"affectedFields\":[],\"question\":\"Please provide more details\"}"))
            .isEmpty();
    }

    private java.util.List<String> resolve(String json) {
        JsonNode result = resolver.resolve(mapper.readTree(json), mapper.missingNode());
        return result.valueStream().map(JsonNode::asText).toList();
    }
}
