package com.aivle.backend.pipeline.conceptportfolio.application;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ConceptPortfolioResultContract {
    public JsonNode validate(JsonNode result) {
        require(result != null && result.isObject());
        require("concept-portfolio-v2-production-result-v1".equals(text(result, "contract")));
        require("1.0".equals(text(result, "contractVersion")));
        require("1.0".equals(text(result, "schemaVersion")));
        int requested = integer(result, "requestedMaxConcepts");
        int produced = integer(result, "producedConceptCount");
        JsonNode concepts = result.get("concepts");
        require(requested >= 1 && requested <= 5 && produced >= 0 && produced <= requested);
        require(concepts != null && concepts.isArray() && concepts.size() == produced);
        require(result.has("userSelectedConceptId") && result.get("userSelectedConceptId").isNull());
        require(result.get("requiredInputs") != null && result.get("requiredInputs").isArray());
        require(result.get("legalSummaries") != null && result.get("legalSummaries").isArray());
        JsonNode artifacts = result.get("continuationArtifacts");
        require(artifacts != null && artifacts.isArray() && artifacts.size() <= 5);
        JsonNode context = result.get("continuationContext");
        if (!artifacts.isEmpty()) {
            require(context != null && context.isObject());
            JsonNode plans = context.get("plans");
            require(plans != null && plans.isArray() && !plans.isEmpty());
            Set<String> planIds = new HashSet<>();
            plans.forEach(plan -> planIds.add(text(plan, "planId")));
            artifacts.forEach(artifact -> require(planIds.contains(text(artifact, "planId"))));
        }
        return result;
    }

    static String text(JsonNode value, String field) {
        JsonNode child = value == null ? null : value.get(field);
        require(child != null && child.isTextual() && !child.asText().isBlank());
        return child.asText();
    }

    static String optionalText(JsonNode value, String field) {
        JsonNode child = value == null ? null : value.get(field);
        if (child == null || child.isNull()) return null;
        require(child.isTextual());
        return child.asText();
    }

    static int integer(JsonNode value, String field) {
        JsonNode child = value == null ? null : value.get(field);
        require(child != null && child.isIntegralNumber());
        return child.intValue();
    }

    static void require(boolean condition) {
        if (!condition) throw new ContractViolation("AI_RESULT_INVALID");
    }

    public static final class ContractViolation extends RuntimeException {
        public ContractViolation(String message) { super(message); }
    }
}
