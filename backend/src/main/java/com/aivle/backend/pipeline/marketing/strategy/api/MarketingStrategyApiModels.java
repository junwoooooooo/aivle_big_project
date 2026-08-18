package com.aivle.backend.pipeline.marketing.strategy.api;

import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class MarketingStrategyApiModels {

    private MarketingStrategyApiModels() {
    }

    public record StrategyActionResponse(
        String reportId,
        String taskRunId,
        String status,
        String sourceManifestHash
    ) {
    }

    public record StrategyView(
        String reportId,
        String taskRunId,
        String status,
        boolean ready,
        boolean stale,
        String sourceManifestHash,
        JsonNode sourceManifest,
        JsonNode result,
        Instant generatedAt,
        List<String> missingSources,
        String projectName
    ) {
    }
}
