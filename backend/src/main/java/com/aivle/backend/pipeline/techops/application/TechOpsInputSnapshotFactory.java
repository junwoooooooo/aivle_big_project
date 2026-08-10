package com.aivle.backend.pipeline.techops.application;

import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import com.aivle.backend.pipeline.techops.domain.TechOpsEvidenceReference;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputPreparation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class TechOpsInputSnapshotFactory {
    public static final String CONTRACT = "tech-ops-input-snapshot-v1";
    public static final String SCHEMA_VERSION = "2.0";
    private final ObjectMapper mapper;
    private final SnapshotHasher hasher;

    public BuiltSnapshot create(String snapshotId, Instant createdAt, TechOpsInputPreparation preparation,
            List<TechOpsEvidenceReference> evidence, Map<String, ProjectEvidenceArtifact> artifacts) {
        JsonNode facts = mapper.readTree(preparation.getRequiredFactsJson());
        JsonNode decisions = mapper.readTree(preparation.getProposalDecisionsJson());
        ObjectNode body = mapper.createObjectNode();
        body.put("contract", CONTRACT); body.put("schemaVersion", SCHEMA_VERSION); body.put("snapshotId", snapshotId);
        body.put("projectId", preparation.getProjectId()); body.put("preparationId", preparation.getId());
        body.put("sourceMarketSeedSnapshotId", preparation.getSourceMarketSeedSnapshotId());
        body.put("sourceSnapshotHash", preparation.getSourceSnapshotHash()); body.put("createdAt", createdAt.toString());
        ObjectNode finalFacts = body.putObject("requiredFacts"); ObjectNode provenance = body.putObject("requiredFactProvenance");
        for (String key : TechOpsPreparationFactory.REQUIRED_FACT_KEYS) {
            finalFacts.set(key, facts.path(key).path("value").deepCopy());
            provenance.set(key, facts.path(key).deepCopy());
        }
        ObjectNode finalDecisions = body.putObject("requiredDecisions");
        for (String key : TechOpsPreparationFactory.PROPOSAL_KEYS) {
            ObjectNode item = finalDecisions.putObject(key); JsonNode source = decisions.path(key);
            item.set("value", source.path("finalValue").deepCopy()); item.put("source", source.path("source").asText());
            item.put("decision", source.path("decision").asText()); item.put("proposalVersion", source.path("proposalVersion").asInt(1));
        }
        ArrayNode refs = body.putArray("evidenceReferences");
        for (TechOpsEvidenceReference value : evidence) {
            ProjectEvidenceArtifact artifact = artifacts.get(value.getArtifactId());
            if (artifact == null || artifact.isDeleted()) throw new IllegalStateException("Evidence artifact is unavailable");
            ObjectNode item = refs.addObject(); item.put("evidenceId", value.getId()); item.put("evidenceType", value.getEvidenceType());
            item.put("artifactId", artifact.getId()); item.put("originalFilename", artifact.getOriginalFilename());
            item.put("displayName", value.getDisplayName()); item.put("mediaType", artifact.getMediaType());
            item.put("sizeBytes", artifact.getSizeBytes()); item.put("sha256", artifact.getSha256());
            if (value.getDescription() != null && !value.getDescription().isBlank()) item.put("description", value.getDescription());
            item.put("source", "USER_PROVIDED_EVIDENCE");
        }
        String hash = hasher.hash(body); body.put("hash", hash);
        return new BuiltSnapshot(body, hash);
    }
    public record BuiltSnapshot(ObjectNode body, String hash) {}
}
