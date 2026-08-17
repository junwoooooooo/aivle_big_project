package com.aivle.backend.pipeline.marketing.strategy.application;

import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class MarketingStrategyResultContract {

    private static final Set<String> ROOT_FIELDS = Set.of(
        "contract",
        "executiveSummary",
        "targetCustomers",
        "positioning",
        "coreMessages",
        "channelStrategies",
        "contentPillars",
        "campaignRoadmap",
        "budgetGuidelines",
        "risks",
        "evidenceRefs"
    );

    private static final Set<String> CHANNEL_FIELDS = Set.of(
        "channel",
        "objective",
        "audience",
        "actions",
        "kpis",
        "rationale"
    );

    private static final Set<String> CAMPAIGN_FIELDS = Set.of(
        "phase",
        "objective",
        "actions",
        "kpis"
    );

    public void validate(JsonNode result) {
        requireObject(result, ROOT_FIELDS, "result");

        if (!"marketing-strategy-result-v1".equals(
                result.path("contract").asText())) {
            throw new IllegalArgumentException(
                "marketing strategy contract is invalid"
            );
        }

        requireText(
            result.path("executiveSummary"),
            "executiveSummary"
        );
        requireStringArray(
            result.path("targetCustomers"),
            "targetCustomers",
            true
        );
        requireText(
            result.path("positioning"),
            "positioning"
        );
        requireStringArray(
            result.path("coreMessages"),
            "coreMessages",
            true
        );
        requireStringArray(
            result.path("contentPillars"),
            "contentPillars",
            true
        );
        requireStringArray(
            result.path("budgetGuidelines"),
            "budgetGuidelines",
            false
        );
        requireStringArray(
            result.path("risks"),
            "risks",
            true
        );
        requireStringArray(
            result.path("evidenceRefs"),
            "evidenceRefs",
            true
        );

        JsonNode channels =
            result.path("channelStrategies");

        if (!channels.isArray() || channels.isEmpty()) {
            throw new IllegalArgumentException(
                "channelStrategies must not be empty"
            );
        }

        for (JsonNode channel : channels) {
            requireObject(
                channel,
                CHANNEL_FIELDS,
                "channelStrategies"
            );
            requireText(channel.path("channel"), "channel");
            requireText(
                channel.path("objective"),
                "objective"
            );
            requireText(
                channel.path("audience"),
                "audience"
            );
            requireText(
                channel.path("rationale"),
                "rationale"
            );
            requireStringArray(
                channel.path("actions"),
                "actions",
                true
            );
            requireStringArray(
                channel.path("kpis"),
                "kpis",
                true
            );
        }

        JsonNode roadmap =
            result.path("campaignRoadmap");

        if (!roadmap.isArray() || roadmap.isEmpty()) {
            throw new IllegalArgumentException(
                "campaignRoadmap must not be empty"
            );
        }

        for (JsonNode phase : roadmap) {
            requireObject(
                phase,
                CAMPAIGN_FIELDS,
                "campaignRoadmap"
            );
            requireText(phase.path("phase"), "phase");
            requireText(
                phase.path("objective"),
                "objective"
            );
            requireStringArray(
                phase.path("actions"),
                "actions",
                true
            );
            requireStringArray(
                phase.path("kpis"),
                "kpis",
                true
            );
        }
    }

    private void requireObject(
        JsonNode value,
        Set<String> fields,
        String name
    ) {
        if (!value.isObject()
                || !Set.copyOf(value.propertyNames())
                    .equals(fields)) {
            throw new IllegalArgumentException(
                name + " fields are invalid"
            );
        }
    }

    private void requireText(
        JsonNode value,
        String name
    ) {
        if (!value.isTextual()
                || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                name + " must not be blank"
            );
        }
    }

    private void requireStringArray(
        JsonNode value,
        String name,
        boolean required
    ) {
        if (!value.isArray()
                || (required && value.isEmpty())) {
            throw new IllegalArgumentException(
                name + " must be an array"
            );
        }

        for (JsonNode item : value) {
            requireText(item, name);
        }
    }
}
