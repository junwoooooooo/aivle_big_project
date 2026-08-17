package com.aivle.backend.pipeline.finance.application;

import com.aivle.backend.pipeline.finance.domain.FinancialInputPreparation;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class FinancialInputSnapshotFactory {
    public static final String CONTRACT = "financial-input-snapshot-v1";
    public static final String SCHEMA_VERSION = "2.0";
    private final ObjectMapper mapper;
    private final SnapshotHasher hasher;
    private final FinancialCalculator calculator;

    public BuiltSnapshot create(String snapshotId, Instant createdAt, FinancialInputPreparation preparation) {
        JsonNode fields = mapper.readTree(preparation.getFinancialFieldsJson());
        ObjectNode body = mapper.createObjectNode();
        body.put("contract", CONTRACT);
        body.put("schemaVersion", SCHEMA_VERSION);
        body.put("snapshotId", snapshotId);
        body.put("projectId", preparation.getProjectId());
        body.put("preparationId", preparation.getId());
        body.put("sourceTechOpsSnapshotId", preparation.getSourceTechOpsSnapshotId());
        body.put("sourceMarketSeedSnapshotId", preparation.getSourceMarketSeedSnapshotId());
        body.put("sourceMarketResearchVersionId", preparation.getSourceMarketResearchVersionId());
        body.put("sourceBusinessModelVersionId", preparation.getSourceBusinessModelVersionId());
        body.put("sourceSnapshotHash", preparation.getSourceSnapshotHash());
        body.put("sourceCurrentMarketSeedSnapshotId", preparation.getSourceCurrentMarketSeedSnapshotId());
        body.put("sourceSelectionId", preparation.getSourceSelectionId());
        body.put("sourceSelectionRevision", preparation.getSourceSelectionRevision());
        body.put("sourceBmPlanRevision", preparation.getSourceBmPlanRevision());
        body.put("currentConceptBindingHash", preparation.getCurrentConceptBindingHash());
        body.put("createdAt", createdAt.toString());
        ObjectNode values = body.putObject("values");
        ObjectNode provenance = body.putObject("valueProvenance");
        for (String key : FinancialPreparationFactory.ALL_KEYS) {
            values.set(key, fields.path(key).path("value").deepCopy());
            provenance.set(key, fields.path(key).deepCopy());
        }
        body.set("calculatedCac", calculator.calculateCac(fields));
        body.set("upstreamReferences", mapper.readTree(preparation.getUpstreamReferencesJson()));
        body.set("assistance", finalizedAssistance(mapper.readTree(preparation.getAssistanceJson())));
        String hash = hasher.hash(body);
        body.put("hash", hash);
        return new BuiltSnapshot(body, hash);
    }

    public BuiltSnapshot createUserDocument(String snapshotId, Instant createdAt,
            FinancialInputPreparation preparation) {
        BuiltSnapshot base = create(snapshotId, createdAt, preparation);
        ObjectNode body = base.body().deepCopy();
        body.remove("hash");
        body.put("sourceMode", "USER_DOCUMENT_INPUT");
        body.put("preparationRevision", preparation.getRevision());
        body.put("sourceDocumentArtifactId", preparation.getSourceDocumentArtifactId());
        body.put("sourceDocumentHash", preparation.getSourceDocumentHash());
        String hash = hasher.hash(body);
        body.put("hash", hash);
        return new BuiltSnapshot(body, hash);
    }

    public BuiltSnapshot createIndependent(String snapshotId, Instant createdAt,
            FinancialInputPreparation preparation) {
        BuiltSnapshot base = create(snapshotId, createdAt, preparation);
        ObjectNode body = base.body().deepCopy();
        body.remove("hash"); body.put("sourceMode", "DIRECT_INPUT");
        body.put("preparationRevision", preparation.getRevision());
        String hash = hasher.hash(body); body.put("hash", hash);
        return new BuiltSnapshot(body, hash);
    }

    private JsonNode finalizedAssistance(JsonNode source) {
        ObjectNode result = (ObjectNode) source.deepCopy();
        for (String key : FinancialPreparationFactory.ALL_KEYS) {
            JsonNode item = result.path(key);
            if (!item.isObject()) continue;
            ObjectNode assistance = (ObjectNode) item;
            assistance.remove("activeTaskRunId");
            assistance.remove("safeError");
            assistance.remove("estimateStatus");
            if ("PROPOSED".equals(assistance.path("decision").asText())) {
                assistance.remove("proposalValue");
                assistance.remove("assumptions");
                assistance.remove("confidence");
            }
        }
        return result;
    }

    public record BuiltSnapshot(ObjectNode body, String hash) {}
}
