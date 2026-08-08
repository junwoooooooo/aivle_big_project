package com.aivle.backend.pipeline.techops.application;

import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.shared.ThreeYearTargetsContract;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class TechOpsPreparationFactory {
    public static final String CONTRACT = "tech-ops-input-preparation-v1";
    public static final String SCHEMA_VERSION = "2.0";
    public static final List<String> REQUIRED_FACT_KEYS = List.of("productServiceSpecification", "targetLaunchDate",
        "ownedPersonnel", "ownedAssetsAndFacilities", "fixedOperatingCost", "initialInvestment", "threeYearTargets");
    public static final List<String> PROPOSAL_KEYS = List.of("deliveryOrProductionMethod",
        "expectedMonthlyThroughputOrSales", "technicalSupplyOperationalConstraints");
    private final ObjectMapper mapper;
    public TechOpsPreparationFactory(ObjectMapper mapper) { this.mapper = mapper; }

    public InitialPreparation create(MarketAnalysisSeedSnapshot snapshot) {
        JsonNode source = mapper.readTree(snapshot.getSnapshotJson());
        ObjectNode facts = mapper.createObjectNode();
        JsonNode product = productSpecification(source);
        fact(facts, "productServiceSpecification", product, "CONCEPT_GENERATED", "REVIEW_REQUIRED", snapshot.getId(), false);
        inheritOrOpen(facts, "targetLaunchDate", source, snapshot);
        inheritOrOpen(facts, "ownedPersonnel", source, snapshot);
        inheritOrOpen(facts, "ownedAssetsAndFacilities", source, snapshot);
        inheritOrOpen(facts, "fixedOperatingCost", source, snapshot);
        inheritOrOpen(facts, "initialInvestment", source, snapshot);
        inheritOrOpen(facts, "threeYearTargets", source, snapshot);

        ObjectNode decisions = mapper.createObjectNode();
        proposal(decisions, "deliveryOrProductionMethod", deliveryProposal(source), "CONCEPT_GENERATED");
        proposal(decisions, "expectedMonthlyThroughputOrSales", null, "AI_HYPOTHESIS");
        proposal(decisions, "technicalSupplyOperationalConstraints", constraintsProposal(source), "ANALYSIS_RESULT");
        return new InitialPreparation(facts, decisions);
    }

    private JsonNode productSpecification(JsonNode source) {
        ObjectNode value = mapper.createObjectNode();
        JsonNode solution = source.path("selectedConcept").path("solution");
        value.put("summary", solution.path("solutionMechanism").asText(source.path("selectedConcept").path("identity").path("conceptDefinition").asText("")));
        value.set("features", solution.path("featureSet").isArray() ? solution.path("featureSet").deepCopy() : mapper.createArrayNode());
        return value;
    }
    private void inheritOrOpen(ObjectNode facts, String key, JsonNode source, MarketAnalysisSeedSnapshot snapshot, String... aliases) {
        JsonNode field = source.path("originalSeed").path("fields").path(key);
        if (field.isMissingNode()) for (String alias : aliases) {
            JsonNode candidate = source.path("originalSeed").path("fields").path(alias);
            if (!candidate.isMissingNode()) { field = candidate; break; }
        }
        JsonNode inherited = field.isMissingNode() ? mapper.nullNode() : decoded(field.path("value"));
        if (!field.isMissingNode() && validInherited(key, inherited))
            fact(facts, key, inherited, field.path("source").asText("USER_INPUT"),
                "LOCKED", snapshot.getId(), true);
        else fact(facts, key, null, "USER_INPUT", "OPEN", null, false);
    }
    private JsonNode decoded(JsonNode value) {
        if (!value.isTextual()) return value.deepCopy();
        String text=value.asText().strip();
        if (text.startsWith("{") || text.startsWith("[")) {
            try { return mapper.readTree(text); } catch (RuntimeException ignored) { return value.deepCopy(); }
        }
        return value.deepCopy();
    }
    private boolean validInherited(String key, JsonNode value) {
        if (!present(value)) return false;
        return switch (key) {
            case "targetLaunchDate" -> value.isTextual() && value.asText().matches("\\d{4}-\\d{2}-\\d{2}");
            case "ownedPersonnel" -> value.isArray();
            case "ownedAssetsAndFacilities" -> value.isArray();
            case "fixedOperatingCost", "initialInvestment" -> value.isObject() && value.path("amount").isNumber()
                && !value.path("currency").asText("").isBlank();
            case "threeYearTargets" -> ThreeYearTargetsContract.valid(value);
            default -> true;
        };
    }
    private void fact(ObjectNode root, String key, JsonNode value, String source, String decision, String sourceId, boolean readOnly) {
        ObjectNode item = root.putObject(key);
        item.set("value", value == null ? mapper.nullNode() : value.deepCopy()); item.put("source", source);
        item.put("decision", decision); item.put("readOnly", readOnly);
        if (sourceId == null) item.putNull("sourceSnapshotId"); else item.put("sourceSnapshotId", sourceId);
    }
    private JsonNode deliveryProposal(JsonNode source) {
        ObjectNode result = mapper.createObjectNode();
        JsonNode solution = source.path("selectedConcept").path("solution");
        JsonNode operation = source.path("selectedConcept").path("operation");
        result.put("method", solution.path("solutionMechanism").asText(""));
        result.put("operatingModel", operation.path("operatingModel").asText(""));
        result.put("partnerModel", operation.path("partnerModel").asText(""));
        return present(result) ? result : null;
    }
    private JsonNode constraintsProposal(JsonNode source) {
        ArrayNode result = mapper.createArrayNode();
        addAll(result, source.path("legalResult").path("requiredControls"));
        addAll(result, source.path("selectedConcept").path("operation").path("partnerRequirements"));
        addAll(result, source.path("selectedConcept").path("operation").path("qualificationRequirements"));
        return result.isEmpty() ? null : result;
    }
    private void addAll(ArrayNode target, JsonNode values) {
        if (!values.isArray()) return;
        List<String> existing = new ArrayList<>(); target.forEach(item -> existing.add(item.asText()));
        for (JsonNode value : values) {
            String text = value.isTextual() ? value.asText() : value.path("text").asText(value.path("value").asText(""));
            if (!text.isBlank() && !existing.contains(text.strip())) { target.add(text.strip()); existing.add(text.strip()); }
        }
    }
    private void proposal(ObjectNode root, String key, JsonNode proposal, String source) {
        ObjectNode item = root.putObject(key); item.set("proposalValue", proposal == null ? mapper.nullNode() : proposal.deepCopy());
        item.putNull("finalValue"); item.put("source", source); item.put("decision", "PROPOSED");
        item.put("proposalVersion", 1); item.put("alternativeRequested", false);
    }
    public static boolean present(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) return false;
        if (value.isTextual()) return !value.asText().isBlank();
        if (value.isArray()) return !value.isEmpty();
        if (value.isObject()) {
            for (JsonNode child : value) if (present(child)) return true;
            return false;
        }
        return true;
    }
    public record InitialPreparation(ObjectNode requiredFacts, ObjectNode proposalDecisions) {}
}
