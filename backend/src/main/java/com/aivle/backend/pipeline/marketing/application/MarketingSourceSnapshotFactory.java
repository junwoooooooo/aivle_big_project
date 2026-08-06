package com.aivle.backend.pipeline.marketing.application;

import com.aivle.backend.pipeline.planning.domain.FinalizedPlanningSnapshot;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.MissingNode;

@Component
@RequiredArgsConstructor
public class MarketingSourceSnapshotFactory {
    private static final int MAX_TEXT = 500;
    private static final int MAX_ITEM_TEXT = 500;
    private final ObjectMapper mapper;
    private final SnapshotHasher hasher;

    public ObjectNode create(FinalizedPlanningSnapshot snapshot) {
        MarketingSourceSnapshot source = map(snapshot);
        return mapper.valueToTree(source);
    }

    public MarketingSourceSnapshot map(FinalizedPlanningSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("finalized planning snapshot is required");
        JsonNode root = mapper.readTree(snapshot.getSnapshotJson());
        JsonNode planning = root.path("planning");
        JsonNode concept = planning.path("finalConcept");
        JsonNode legal = root.path("legalControls");
        MarketingSourceSnapshot unhashed = new MarketingSourceSnapshot(
            text(concept, "conceptName", "name"),
            text(planning, "finalTarget", "targetSegment"),
            text(concept, "problem", "problemScenario"),
            text(planning, "finalValueProposition", "valueProposition"),
            text(concept, "positioning", "oneLineSummary"),
            requiredList(planning, "finalFeatures", "keyFeatures"),
            text(planning, "finalPricingRevenueHypothesis", "pricing"),
            requiredList(planning, "finalChannels", "channels"),
            list(concept, "competitorDifferentiators", "differentiators"),
            list(legal, "allowedClaims"),
            list(legal, "prohibitedExpressions", "prohibitedClaims"),
            list(legal, "requiredDisclosures"),
            null
        );
        ObjectNode canonical = mapper.valueToTree(unhashed);
        canonical.remove("sourceSnapshotHash");
        return new MarketingSourceSnapshot(
            unhashed.conceptName(), unhashed.targetSegment(), unhashed.problem(),
            unhashed.valueProposition(), unhashed.positioning(), unhashed.keyFeatures(),
            unhashed.pricing(), unhashed.channels(), unhashed.competitorDifferentiators(),
            unhashed.allowedClaims(), unhashed.prohibitedClaims(), unhashed.requiredDisclosures(),
            hasher.hash(canonical)
        );
    }

    public boolean matches(FinalizedPlanningSnapshot snapshot, String expectedHash) {
        return expectedHash != null && expectedHash.equals(map(snapshot).sourceSnapshotHash());
    }

    private String text(JsonNode source, String... keys) {
        JsonNode value = first(source, keys);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > MAX_TEXT) {
            throw new IllegalArgumentException("finalized planning text is missing or invalid");
        }
        return value.asText().strip();
    }

    private List<String> requiredList(JsonNode source, String... keys) {
        List<String> values = list(source, keys);
        if (values.isEmpty()) throw new IllegalArgumentException("finalized planning list is required");
        return values;
    }

    private List<String> list(JsonNode source, String... keys) {
        JsonNode value = first(source, keys);
        if (value.isMissingNode() || value.isNull()) return List.of();
        if (!value.isArray() || value.size() > 30) throw new IllegalArgumentException("finalized planning list is invalid");
        List<String> values = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > MAX_ITEM_TEXT) {
                throw new IllegalArgumentException("finalized planning list item is invalid");
            }
            values.add(item.asText().strip());
        }
        return List.copyOf(values);
    }

    private JsonNode first(JsonNode source, String... keys) {
        for (String key : keys) {
            if (source.has(key) && !source.get(key).isNull()) return source.get(key);
        }
        return MissingNode.getInstance();
    }
}
