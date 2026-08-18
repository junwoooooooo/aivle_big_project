package com.aivle.backend.pipeline.finalreport.api;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class FinalReportApiModels {
    private FinalReportApiModels() {}

    public enum State { CURRENT, STALE, READY, NOT_READY, GENERATING, REVIEWING }

    public record ReadinessItem(String journeyId, String label, String status) {}

    public record FinalReportStatusView(
        State state,
        Integer currentVersion,
        Instant generatedAt,
        boolean stale,
        String taskRunId,
        List<String> blockingSources,
        List<String> availableSources,
        List<String> omittedSources,
        Map<String, String> sourceStates,
        String lastTaskRunId,
        String lastState,
        String lastErrorCode,
        String lastErrorReason
    ) {}

    public record GenerateRequest(List<String> includedOptionalSources) {}

    public record ProposalActionResponse(String reportId, String taskRunId, String status,
                                         String sourceManifestHash) {}

    public record ReviewView(String taskRunId, String status, JsonNode result, Instant generatedAt) {}

    public record FinalReportView(
        State state,
        String snapshotId,
        Integer version,
        Instant generatedAt,
        String sourceManifestHash,
        JsonNode sourceManifest,
        JsonNode report,
        Long generatedByUserId,
        String generatedByName,
        List<ReadinessItem> readiness,
        List<String> missingSources,
        List<String> blockingSources,
        List<String> omittedSources
    ) {}
}
