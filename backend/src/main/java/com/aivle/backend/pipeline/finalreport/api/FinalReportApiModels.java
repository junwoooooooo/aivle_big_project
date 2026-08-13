package com.aivle.backend.pipeline.finalreport.api;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public final class FinalReportApiModels {
    private FinalReportApiModels() {}

    public enum State { CURRENT, STALE, NOT_READY }

    public record ReadinessItem(String journeyId, String label, String status) {}

    public record FinalReportView(
        State state,
        String snapshotId,
        Integer version,
        Instant generatedAt,
        String sourceManifestHash,
        JsonNode sourceManifest,
        JsonNode report,
        List<ReadinessItem> readiness,
        List<String> missingSources
    ) {}
}
