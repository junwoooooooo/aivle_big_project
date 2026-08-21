package com.aivle.backend.pipeline.conceptportfolio.selection.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Canonical persisted value contract shared by all Concept Portfolio hypothesis boundaries. */
public final class HypothesisValueContract {
    private static final Set<PortfolioHypothesisType> TEXT_TYPES = Set.of(
        PortfolioHypothesisType.TARGET_REGION,
        PortfolioHypothesisType.REVENUE_MODEL,
        PortfolioHypothesisType.PRICE,
        PortfolioHypothesisType.CHANNELS,
        PortfolioHypothesisType.DIFFERENTIATORS);
    private static final Set<PortfolioHypothesisType> LIST_COMPATIBLE_TEXT_TYPES = Set.of(
        PortfolioHypothesisType.CHANNELS,
        PortfolioHypothesisType.DIFFERENTIATORS);
    private static final Set<String> SHARE_FIELDS = Set.of(
        "targetSharePercent", "horizonYears", "rationale", "assumptions");
    private static final Set<String> SOM_FIELDS = Set.of(
        "amount", "currency", "period", "calculationBasis", "assumptions", "confidence");

    private HypothesisValueContract() { }

    public static JsonNode canonicalize(ObjectMapper mapper, PortfolioHypothesisType type, JsonNode value) {
        if (mapper == null || type == null || value == null || value.isNull() || value.isMissingNode()) {
            throw new IllegalArgumentException("hypothesis value is required");
        }
        if (TEXT_TYPES.contains(type)) {
            if (value.isTextual()) {
                if (value.asText().isBlank()) throw new IllegalArgumentException("text hypothesis is blank");
                return value.deepCopy();
            }
            if (LIST_COMPATIBLE_TEXT_TYPES.contains(type) && value.isArray()) {
                List<String> items = new ArrayList<>();
                for (JsonNode item : value) {
                    if (!item.isTextual() || item.asText().isBlank()) {
                        throw new IllegalArgumentException("list-compatible hypothesis contains a non-text item");
                    }
                    items.add(item.asText().strip().replaceAll("\\s+", " "));
                }
                if (items.isEmpty()) throw new IllegalArgumentException("list-compatible hypothesis is empty");
                return mapper.getNodeFactory().textNode(String.join(", ", items));
            }
            throw new IllegalArgumentException("text hypothesis must be a string");
        }
        if (!value.isObject()) throw new IllegalArgumentException("SOM hypothesis must be an object");
        if (type == PortfolioHypothesisType.PRE_MARKET_SOM_SHARE) validateShare(value);
        else if (type == PortfolioHypothesisType.PRE_MARKET_SOM) validateSom(value);
        else throw new IllegalArgumentException("unsupported hypothesis type");
        return value.deepCopy();
    }

    private static void validateShare(JsonNode value) {
        requireExactFields(value, SHARE_FIELDS);
        double share = value.path("targetSharePercent").asDouble(Double.NaN);
        int years = value.path("horizonYears").asInt(0);
        if (!value.path("targetSharePercent").isNumber() || !Double.isFinite(share) || share <= 0 || share > 100
                || !value.path("horizonYears").isIntegralNumber() || years < 1 || years > 10
                || !text(value.path("rationale")) || !textArray(value.path("assumptions"))) {
            throw new IllegalArgumentException("PRE_MARKET_SOM_SHARE contract is invalid");
        }
    }

    private static void validateSom(JsonNode value) {
        requireExactFields(value, SOM_FIELDS);
        double amount = value.path("amount").asDouble(Double.NaN);
        String confidence = value.path("confidence").asText();
        if (!value.path("amount").isNumber() || !Double.isFinite(amount) || amount < 0
                || !text(value.path("currency")) || !text(value.path("period"))
                || !text(value.path("calculationBasis")) || !textArray(value.path("assumptions"))
                || !Set.of("LOW", "MEDIUM", "HIGH").contains(confidence)) {
            throw new IllegalArgumentException("PRE_MARKET_SOM contract is invalid");
        }
    }

    private static void requireExactFields(JsonNode value, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        actual.addAll(value.propertyNames());
        if (!actual.equals(expected)) throw new IllegalArgumentException("structured hypothesis fields are invalid");
    }

    private static boolean text(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank();
    }

    private static boolean textArray(JsonNode value) {
        if (!value.isArray() || value.isEmpty()) return false;
        for (JsonNode item : value) if (!text(item)) return false;
        return true;
    }
}
