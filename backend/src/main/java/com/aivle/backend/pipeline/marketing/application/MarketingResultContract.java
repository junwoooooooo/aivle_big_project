package com.aivle.backend.pipeline.marketing.application;

import com.aivle.backend.pipeline.marketing.domain.MarketingContentType;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class MarketingResultContract {
    private static final Set<String> ROOT = Set.of("contract", "contentType", "title", "body", "callToAction", "hashtags", "imageBrief", "legalReview", "artifactRefs");
    private static final Set<String> LEGAL = Set.of("compliant", "warnings", "requiredDisclosuresApplied");

    public void validate(JsonNode value, MarketingContentType expectedType) {
        if (value == null || !value.isObject() || !Set.copyOf(value.propertyNames()).equals(ROOT)
            || !"marketing-content-result-v1".equals(value.path("contract").asText())
            || !expectedType.name().equals(value.path("contentType").asText())
            || !text(value.get("title"), 200) || !text(value.get("body"), 20_000)
            || !nullableText(value.get("callToAction"), 500) || !nullableText(value.get("imageBrief"), 4_000)
            || !stringArray(value.get("hashtags"), 30, 100) || !artifactRefs(value.get("artifactRefs"))) invalid();
        JsonNode legal = value.get("legalReview");
        if (legal == null || !legal.isObject() || !Set.copyOf(legal.propertyNames()).equals(LEGAL)
            || !legal.path("compliant").isBoolean() || !stringArray(legal.get("warnings"), 30, 500)
            || !stringArray(legal.get("requiredDisclosuresApplied"), 30, 500)) invalid();
    }
    private boolean text(JsonNode n, int max) { return n != null && n.isTextual() && !n.asText().isBlank() && n.asText().length() <= max; }
    private boolean nullableText(JsonNode n, int max) { return n != null && (n.isNull() || (n.isTextual() && !n.asText().isBlank() && n.asText().length() <= max)); }
    private boolean stringArray(JsonNode n, int maxItems, int maxLength) {
        if (n == null || !n.isArray() || n.size() > maxItems) return false;
        for (JsonNode item : n) if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > maxLength) return false;
        return true;
    }
    private boolean artifactRefs(JsonNode value) {
        if (!stringArray(value, 1, 300)) return false;
        for (JsonNode item : value) {
            if (!item.asText().matches("ai-artifacts/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.jpg")) return false;
        }
        return true;
    }
    private void invalid() { throw new IllegalArgumentException("marketing content result violates closed schema"); }
}
