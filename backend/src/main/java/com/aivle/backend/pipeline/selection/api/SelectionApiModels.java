package com.aivle.backend.pipeline.selection.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public final class SelectionApiModels {
    private SelectionApiModels() {}
    public record CreateSelectionRequest(@NotBlank String conceptId, @NotBlank @Size(max = 2000) String selectionReason) {}
    public record SelectionResponse(Long selectionId, String conceptId, String selectionReason, Instant selectedAt,
                                    boolean current, SnapshotResponse snapshot) {}
    public record SnapshotResponse(String snapshotId, int sequence, String parentSnapshotId, String snapshotHash,
                                   String sourceConceptHash, Instant selectedAt, JsonNode body) {}
}
