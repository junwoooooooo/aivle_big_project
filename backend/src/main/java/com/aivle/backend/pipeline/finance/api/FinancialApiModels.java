package com.aivle.backend.pipeline.finance.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class FinancialApiModels {
    private FinancialApiModels() {}
    public record FinancialFieldsPatch(@NotNull JsonNode values) {}
    public record PreparationView(String contract, String schemaVersion, String preparationId, Long projectId,
        String sourceTechOpsSnapshotId, String sourceMarketSeedSnapshotId, String sourceSnapshotHash,
        int revision, JsonNode financialFields, JsonNode upstreamReferences, JsonNode assistance,
        JsonNode calculatedCac, List<String> missingRequiredInputs, boolean readyToFinalize,
        String inputSnapshotId, LocalDateTime updatedAt) {}
    public record SnapshotView(String contract, String snapshotId, String schemaVersion, Long projectId,
        String preparationId, String sourceTechOpsSnapshotId, String sourceMarketSeedSnapshotId,
        String snapshotHash, Instant createdAt, JsonNode snapshot) {}
}
