package com.aivle.backend.pipeline.concept.domain;

import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class ConceptSemanticDistinctnessResult {
    private static final Set<String> FIELDS = Set.of(
        "decision", "overlappingDimensions", "materiallyDifferentDimensions", "safeSummary");

    private ConceptSemanticDistinctnessResult() {}

    public static Decision validate(JsonNode result) {
        if (result == null || !result.isObject() || !Set.copyOf(result.propertyNames()).equals(FIELDS)
                || !Set.of("DISTINCT", "DUPLICATE").contains(result.path("decision").asText())
                || !textArray(result.path("overlappingDimensions"))
                || !textArray(result.path("materiallyDifferentDimensions"))
                || !result.path("safeSummary").isTextual() || result.path("safeSummary").asText().isBlank()) {
            throw new IllegalArgumentException("semantic distinctness result invalid");
        }
        return Decision.valueOf(result.path("decision").asText());
    }

    private static boolean textArray(JsonNode values) {
        if (!values.isArray() || values.size() > 13) return false;
        for (JsonNode value : values) if (!value.isTextual() || value.asText().isBlank()) return false;
        return true;
    }

    public enum Decision { DISTINCT, DUPLICATE }
}
