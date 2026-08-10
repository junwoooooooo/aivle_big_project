package com.aivle.backend.pipeline.shared;

import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class ThreeYearTargetsContract {
    private static final Set<String> METRICS = Set.of(
        "salesVolume", "customerCount", "subscriberCount", "transactionCount");

    private ThreeYearTargetsContract() {}

    public static boolean valid(JsonNode value) {
        if (value == null || !value.isObject() || !METRICS.contains(value.path("metric").asText())
                || value.path("unit").asText().isBlank() || !value.path("years").isArray()
                || value.path("years").size() != 3) return false;
        boolean[] years = new boolean[4];
        for (JsonNode item : value.path("years")) {
            int year = item.path("year").asInt(-1);
            if (!item.isObject() || year < 1 || year > 3 || years[year]
                    || !item.path("value").isNumber() || item.path("value").asDouble() < 0) return false;
            years[year] = true;
        }
        return years[1] && years[2] && years[3];
    }
}
