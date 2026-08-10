package com.aivle.backend.pipeline.marketseed.api;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public final class MarketAnalysisSeedApiModels {
    private MarketAnalysisSeedApiModels() {}

    public record SnapshotView(String contract, String snapshotId, String schemaVersion, Long projectId,
        Long selectionId, String conceptId, String sourceSnapshotHash, String snapshotHash,
        Instant createdAt, JsonNode snapshot) {}
}
