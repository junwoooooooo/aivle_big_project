package com.aivle.backend.pipeline.marketing.api;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public final class MarketingSourceApiModels {
    private MarketingSourceApiModels() {}
    public record SnapshotView(String contract, String snapshotId, String schemaVersion, Long projectId,
        Long selectionId, String conceptId, String marketAnalysisSeedSnapshotId, String snapshotHash,
        Instant createdAt, JsonNode snapshot) {}
}
