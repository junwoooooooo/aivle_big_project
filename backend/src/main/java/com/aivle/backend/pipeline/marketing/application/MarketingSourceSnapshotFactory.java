package com.aivle.backend.pipeline.marketing.application;

import com.aivle.backend.pipeline.planning.domain.FinalizedPlanningSnapshot;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.*;

@Component @RequiredArgsConstructor
public class MarketingSourceSnapshotFactory {
    private final ObjectMapper mapper;
    private final SnapshotHasher hasher;

    public ObjectNode create(FinalizedPlanningSnapshot snapshot) {
        JsonNode root = mapper.readTree(snapshot.getSnapshotJson());
        JsonNode planning = root.path("planning");
        JsonNode concept = planning.path("finalConcept");
        JsonNode legal = root.path("legalControls");
        ObjectNode source = mapper.createObjectNode();
        source.set("conceptName", first(concept, "conceptName", "name"));
        source.set("targetSegment", first(planning, "finalTarget", "targetSegment"));
        source.set("problem", first(concept, "problem", "problemScenario"));
        source.set("valueProposition", first(planning, "finalValueProposition", "valueProposition"));
        source.set("positioning", first(concept, "positioning", "oneLineSummary"));
        source.set("keyFeatures", first(planning, "finalFeatures", "keyFeatures"));
        source.set("pricing", first(planning, "finalPricingRevenueHypothesis", "pricing"));
        source.set("channels", first(planning, "finalChannels", "channels"));
        source.set("competitorDifferentiators", first(concept, "competitorDifferentiators", "differentiators"));
        source.set("allowedClaims", first(legal, "allowedClaims"));
        source.set("prohibitedClaims", first(legal, "prohibitedExpressions", "prohibitedClaims"));
        source.set("requiredDisclosures", first(legal, "requiredDisclosures"));
        source.put("sourceSnapshotHash", hasher.hash(source));
        return source;
    }

    private JsonNode first(JsonNode source, String... keys) {
        for (String key : keys) if (source.has(key) && !source.get(key).isNull()) return source.get(key).deepCopy();
        return NullNode.getInstance();
    }
}
