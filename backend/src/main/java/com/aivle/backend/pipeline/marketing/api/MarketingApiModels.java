package com.aivle.backend.pipeline.marketing.api;

import com.aivle.backend.pipeline.marketing.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class MarketingApiModels {
    private MarketingApiModels() {}
    public record CreateRequest(
        @NotBlank String contract, @NotBlank String marketingSourceSnapshotId,
        @NotNull MarketingContentType contentType, @NotBlank @Size(max=120) String channel,
        @NotBlank @Size(max=500) String purpose, @NotBlank @Size(max=100) String tone,
        @NotNull Length length, @Size(max=20) List<@Size(max=200) String> requiredPhrases,
        @Size(max=20) List<@Size(max=200) String> excludedPhrases,
        @Size(max=2000) String additionalInstruction,
        @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        String referenceArtifactId) {}
    public enum Length { SHORT, MEDIUM, LONG }
    public record EditRequest(@NotNull MarketingRevisionType revisionType, @NotNull JsonNode result) {}
    public record ContentSummary(String contentId, String marketingSourceSnapshotId, String sourceSnapshotHash,
        MarketingContentType contentType, String channel, String title, String status,
        int currentRevisionNumber, String taskRunId, String activeJobId, String sourceSnapshotId,
        LocalDateTime updatedAt, Instant finalizedAt) {}
    public record RevisionView(String revisionId, int revisionNumber, MarketingRevisionType revisionType,
        MarketingRevisionOrigin origin, JsonNode result) {}
    public record ContentView(ContentSummary content, JsonNode sourceSnapshot, JsonNode request,
        List<RevisionView> revisions, List<String> artifactRefs) {}
    public record ContentListView(List<ContentSummary> contents) {}
}
