package com.aivle.backend.pipeline.marketing.application;

import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class MarketingSourceSnapshotFactory {
    public static final String CONTRACT = "marketing-source-snapshot-v1";
    public static final String SCHEMA_VERSION = "2.0";
    private final ObjectMapper mapper;
    private final SnapshotHasher hasher;

    public BuiltSnapshot create(String snapshotId, Instant createdAt, MarketAnalysisSeedSnapshot marketSeed, Concept concept) {
        return create(snapshotId, createdAt, marketSeed, concept.getTitle(), concept.getSummary(),
            mapper.readTree(concept.getCandidateJson()));
    }

    public BuiltSnapshot create(String snapshotId, Instant createdAt, MarketAnalysisSeedSnapshot marketSeed,
            ConceptPortfolioConcept concept) {
        JsonNode envelope = mapper.readTree(concept.getCandidateSnapshotJson());
        return create(snapshotId, createdAt, marketSeed, concept.getConceptName(), concept.getSummary(),
            envelope.path("candidate"));
    }

    private BuiltSnapshot create(String snapshotId, Instant createdAt, MarketAnalysisSeedSnapshot marketSeed,
            String fallbackName, String fallbackSummary, JsonNode candidate) {
        JsonNode seed = mapper.readTree(marketSeed.getSnapshotJson());
        JsonNode identity = seed.path("selectedConcept").path("identity");
        JsonNode solution = seed.path("selectedConcept").path("solution");
        JsonNode hypotheses = seed.path("finalHypotheses");
        JsonNode legal = seed.path("legalResult");
        boolean portfolio = "CONCEPT_PORTFOLIO_V2".equals(marketSeed.getSourceType());
        Long selectionId = portfolio ? marketSeed.getPortfolioSelectionId() : marketSeed.getSelectionId();
        String conceptId = portfolio ? marketSeed.getPortfolioConceptId() : marketSeed.getConceptId();

        ObjectNode body = mapper.createObjectNode();
        body.put("contract", CONTRACT); body.put("schemaVersion", SCHEMA_VERSION);
        body.put("snapshotId", snapshotId); body.put("projectId", marketSeed.getProjectId());
        body.put("selectionId", selectionId); body.put("conceptId", conceptId);
        body.put("marketAnalysisSeedSnapshotId", marketSeed.getId());
        body.put("marketAnalysisSeedSnapshotHash", marketSeed.getSnapshotHash()); body.put("createdAt", createdAt.toString());
        body.put("conceptName", text(identity, "conceptName", fallbackName));
        body.put("targetSegment", text(identity, "targetUsers", fallbackSummary));
        body.put("problem", text(solution, "problemScenario", fallbackSummary));
        body.put("valueProposition", text(identity, "coreValue", text(solution, "solutionMechanism", fallbackSummary)));
        body.put("positioning", text(identity, "conceptDefinition", text(identity, "introduction", fallbackSummary)));
        body.set("keyFeatures", strings(solution.path("featureSet"), text(solution, "solutionMechanism", fallbackSummary)));
        body.put("targetRegion", display(value(hypotheses, "targetRegion")));
        body.put("revenueModel", display(value(hypotheses, "revenueModel")));
        body.put("price", display(value(hypotheses, "price")));
        body.put("pricing", display(value(hypotheses, "revenueModel")) + " · " + display(value(hypotheses, "price")));
        body.set("channels", strings(value(hypotheses, "channels"), null));
        body.set("competitorDifferentiators", strings(value(hypotheses, "differentiators"), null));
        body.set("preMarketSomShare", value(hypotheses, "preMarketSomShare"));
        body.set("preMarketSom", value(hypotheses, "preMarketSom"));
        body.put("legalStatus", legal.path("legalStatus").asText());
        body.set("allowedClaims", strings(candidate.path("advertisingClaims"), null));
        body.set("prohibitedClaims", legalStrings(legal.path("prohibitedVariants")));
        body.set("requiredDisclosures", legalStrings(legal.path("requiredDisclosures")));
        ArrayNode controls = legalStrings(legal.path("requiredControls"));
        body.set("requiredControls", controls.deepCopy());
        body.set("communicationRequiredControls", controls.deepCopy());
        body.set("officialEvidenceReferences", evidenceReferences(legal.path("officialEvidenceReferences")));
        String hash = hasher.hash(body);
        body.put("hash", hash);
        body.put("sourceSnapshotHash", hash);
        return new BuiltSnapshot(body, hash);
    }

    private JsonNode value(JsonNode hypotheses, String key) {
        JsonNode field = hypotheses.path(key);
        return field.has("value") ? field.path("value").deepCopy() : field.deepCopy();
    }
    private String text(JsonNode node, String key, String fallback) {
        String value = node.path(key).asText("").strip();
        return value.isBlank() ? fallback : value;
    }
    private String display(JsonNode value) {
        if (value.isTextual()) return value.asText();
        return mapper.writeValueAsString(value);
    }
    private ArrayNode strings(JsonNode value, String fallback) {
        ArrayNode result = mapper.createArrayNode();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (value.isArray()) for (JsonNode item : value) add(unique, item.asText(""));
        else if (value.isTextual()) add(unique, value.asText());
        if (unique.isEmpty()) add(unique, fallback);
        unique.forEach(result::add);
        return result;
    }
    private ArrayNode legalStrings(JsonNode values) {
        List<String> result = new ArrayList<>();
        if (values.isArray()) for (JsonNode value : values) {
            String text = value.isTextual() ? value.asText() : value.path("text").asText(value.path("value").asText(""));
            if (!text.isBlank() && !result.contains(text.strip())) result.add(text.strip());
        }
        ArrayNode array = mapper.createArrayNode(); result.forEach(array::add); return array;
    }
    private ArrayNode evidenceReferences(JsonNode values) {
        ArrayNode result = mapper.createArrayNode();
        if (!values.isArray()) return result;
        for (JsonNode value : values) {
            if (!value.isObject()) continue;
            ObjectNode item = result.addObject();
            if (value.path("referenceIndex").canConvertToInt()) item.put("referenceIndex", value.path("referenceIndex").asInt());
            copyText(value, item, "sourceType", "lawId", "officialIdentifier", "lawName", "articleReference",
                "title", "officialSourceUri", "jurisdiction", "promulgationDate", "effectiveDate", "retrievedAt",
                "contentHash", "registryVersion");
        }
        return result;
    }
    private void copyText(JsonNode source, ObjectNode target, String... fields) {
        for (String field : fields) {
            JsonNode value = source.get(field);
            if (value != null && value.isTextual()) target.put(field, value.asText());
        }
    }
    private void add(LinkedHashSet<String> values, String value) { if (value != null && !value.isBlank()) values.add(value.strip()); }

    public record BuiltSnapshot(ObjectNode body, String hash) {}
}
