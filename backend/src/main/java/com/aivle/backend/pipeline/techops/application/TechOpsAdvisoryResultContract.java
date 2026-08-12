package com.aivle.backend.pipeline.techops.application;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class TechOpsAdvisoryResultContract {
    private static final Set<String> FIELDS = Set.of("productName", "decision", "summary", "advice",
        "gates", "operatingCosts", "readiness", "pilotPlan", "disclaimer", "layer1Facts", "layer2Evidence");
    private static final Set<String> ADVICE = Set.of("MARKET_BM", "PRODUCT_TECH", "OPERATIONS",
        "RISK_GATE", "PARTNER_SUPPLY", "PILOT", "SCALE");
    private static final Set<String> READINESS = Set.of("DATA_AI", "CUSTOMER_TRUST", "OBSERVABILITY_SLA", "SCALABILITY");

    public void validate(JsonNode result) {
        require(result != null && result.isObject() && Set.copyOf(result.propertyNames()).equals(FIELDS));
        require(text(result, "productName") && text(result, "summary") && text(result, "disclaimer"));
        require(Set.of("GO", "CONDITIONAL_GO", "REVISE", "NO_GO").contains(result.path("decision").asText()));
        require(result.path("advice").isArray() && result.path("advice").size() == 7);
        require(result.path("readiness").isArray() && result.path("readiness").size() == 4);
        require(result.path("gates").isArray() && result.path("gates").size() >= 6);
        require(result.path("operatingCosts").isArray() && result.path("operatingCosts").size() >= 5);
        JsonNode pilot = result.path("pilotPlan");
        require(pilot.isObject() && text(pilot, "objective") && nonEmpty(pilot, "scope")
            && nonEmpty(pilot, "metrics") && nonEmpty(pilot, "stopConditions") && nonEmpty(pilot, "scaleConditions"));
        Set<String> basis = new HashSet<>();
        result.path("layer1Facts").forEach(value -> addId(basis, value, "factId", "FACT-"));
        result.path("layer2Evidence").forEach(value -> addId(basis, value, "evidenceId", null));
        require(!basis.isEmpty());
        Set<String> areas = new HashSet<>();
        result.path("advice").forEach(value -> { areas.add(value.path("area").asText()); validateBasis(value, basis); });
        require(areas.equals(ADVICE));
        Set<String> topics = new HashSet<>();
        result.path("readiness").forEach(value -> { topics.add(value.path("topic").asText()); validateBasis(value, basis); });
        require(topics.equals(READINESS));
        result.path("gates").forEach(value -> validateBasis(value, basis));
        result.path("operatingCosts").forEach(value -> validateBasis(value, basis));
    }

    private void validateBasis(JsonNode value, Set<String> allowed) {
        JsonNode ids = value.path("basisIds"); require(ids.isArray() && !ids.isEmpty());
        ids.forEach(id -> require(id.isTextual() && allowed.contains(id.asText())));
    }
    private void addId(Set<String> ids, JsonNode value, String field, String prefix) {
        String id = value.path(field).asText(""); require(!id.isBlank() && (prefix == null || id.startsWith(prefix)));
        require(ids.add(id));
    }
    private boolean nonEmpty(JsonNode value, String field) { return value.path(field).isArray() && !value.path(field).isEmpty(); }
    private boolean text(JsonNode value, String field) { return value.path(field).isTextual() && !value.path(field).asText().isBlank(); }
    private void require(boolean condition) { if (!condition) throw new IllegalStateException("TechOps advisory contract invalid"); }
}
