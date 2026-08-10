package com.aivle.backend.pipeline.marketseed.application;

import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalAssessment;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalEvidenceLink;
import com.aivle.backend.pipeline.selection.domain.ConceptHypothesisDecision;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.selection.domain.HypothesisType;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class MarketAnalysisSeedSnapshotFactory {
    public static final String CONTRACT = "market-analysis-seed-snapshot-v1";
    public static final String SCHEMA_VERSION = "2.0";
    private static final Set<String> REQUIRED_SEED = Set.of("ideaOverview", "problem", "targetUsers");
    private final ObjectMapper mapper;

    public MarketAnalysisSeedSnapshotFactory(ObjectMapper mapper) { this.mapper = mapper; }

    public ObjectNode create(String snapshotId, Instant createdAt, ConceptSelection selection, Concept concept,
            IdeaBrief brief, List<IdeaBriefField> fields, List<ConceptHypothesisDecision> decisions,
            ConceptLegalAssessment legal, List<ConceptLegalEvidenceLink> evidenceLinks) {
        ObjectNode root = mapper.createObjectNode();
        root.put("contract", CONTRACT);
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("snapshotId", snapshotId);
        root.put("projectId", selection.getProjectId());
        root.put("selectionId", selection.getId());
        root.put("conceptId", concept.getId());
        root.put("createdAt", createdAt.toString());
        root.put("sourceSnapshotHash", concept.getSourceSnapshotHash());
        root.set("originalSeed", originalSeed(brief, fields));
        root.set("aiInterpretation", mapper.readTree(brief.getInterpretationJson()));
        JsonNode candidate = mapper.readTree(concept.getCandidateJson());
        root.set("selectedConcept", selectedConcept(concept, candidate));
        root.set("finalHypotheses", finalHypotheses(candidate, decisions));
        root.set("legalResult", legalResult(legal, decisions, evidenceLinks));
        return root;
    }

    private ObjectNode originalSeed(IdeaBrief brief, List<IdeaBriefField> fields) {
        ObjectNode seed = mapper.createObjectNode();
        seed.put("ideaOverview", brief.getOverviewText());
        ObjectNode values = seed.putObject("fields");
        fields.stream().filter(field -> REQUIRED_SEED.contains(field.getFieldKey())
                || "LOCKED".equals(field.getDecisionState().name()))
            .sorted(Comparator.comparing(IdeaBriefField::getFieldKey)).forEach(field -> {
                ObjectNode item = values.putObject(field.getFieldKey());
                item.put("value", field.getFieldValue());
                item.put("source", field.getProvenance().name());
                item.put("decisionState", field.getDecisionState().name());
            });
        return seed;
    }

    private ObjectNode selectedConcept(Concept concept, JsonNode candidate) {
        ObjectNode selected = mapper.createObjectNode();
        ObjectNode identity = selected.putObject("identity");
        copy(candidate, identity, "conceptName", "conceptDefinition", "introduction", "coreValue",
            "targetUsers", "industryCategory", "researchScope");
        ObjectNode solution = selected.putObject("solution");
        copy(candidate, solution, "problemScenario", "solutionMechanism", "featureSet");
        ObjectNode operation = selected.putObject("operation");
        copy(candidate, operation, "actorRoles", "platformRole", "operatingModel", "partnerModel",
            "providerRole", "sellerRole", "intermediaryRole", "transactionFlow", "paymentFlow",
            "personalDataUsage", "physicalActivities", "partnerRequirements", "qualificationRequirements");
        selected.set("valueSemantics", candidate.path("valueSemantics").deepCopy());
        selected.put("canonicalHash", concept.getCanonicalHash());
        return selected;
    }

    private ObjectNode finalHypotheses(JsonNode candidate, List<ConceptHypothesisDecision> decisions) {
        ObjectNode result = mapper.createObjectNode();
        Map<HypothesisType, String> keys = Map.of(
            HypothesisType.TARGET_REGION, "targetRegion",
            HypothesisType.REVENUE_MODEL, "revenueModel", HypothesisType.PRICE, "price",
            HypothesisType.CHANNELS, "channels", HypothesisType.DIFFERENTIATORS, "differentiators",
            HypothesisType.PRE_MARKET_SOM_SHARE, "preMarketSomShare", HypothesisType.PRE_MARKET_SOM, "preMarketSom");
        decisions.stream().sorted(Comparator.comparing(value -> value.getHypothesisType().ordinal())).forEach(decision -> {
            ObjectNode item = result.putObject(keys.get(decision.getHypothesisType()));
            item.set("value", mapper.readTree(decision.getFinalValueJson()));
            item.put("source", decision.getSource());
            item.put("decisionStatus", decision.getDecisionStatus().name());
            item.put("proposalVersion", decision.getProposalVersion());
            item.put("legalImpact", decision.getLegalImpact().name());
            item.put("legalReviewStatus", decision.getLegalReviewStatus().name());
            if (decision.getDecidedAt() != null) item.put("decidedAt", decision.getDecidedAt().toString());
        });
        return result;
    }

    private ObjectNode legalResult(ConceptLegalAssessment legal, List<ConceptHypothesisDecision> decisions,
            List<ConceptLegalEvidenceLink> evidenceLinks) {
        JsonNode assessment = mapper.readTree(legal.getAssessmentJson());
        ObjectNode result = mapper.createObjectNode();
        result.put("legalStatus", legal.getStatus().name());
        result.put("safeSummary", legal.getSafeSummary());
        copyAs(assessment, result, "requiredControls", "requiredControls");
        copyAs(assessment, result, "requiredPartnersAndQualifications", "requiredPartnersAndQualifications");
        copyAs(assessment, result, "prohibitedVariants", "prohibitedVariants");
        copyAs(assessment, result, "requiredDisclosures", "requiredDisclosures");
        ArrayNode evidence = result.putArray("officialEvidenceReferences");
        Set<String> evidenceKeys = new HashSet<>();
        evidenceLinks.stream().map(ConceptLegalEvidenceLink::getEvidence)
            .sorted(Comparator.comparing(value -> value.getOfficialSourceUri())).forEach(value -> {
                ObjectNode item = evidence.addObject();
                item.put("sourceType", value.getSourceType()); item.put("lawId", value.getLawId());
                item.put("officialIdentifier", value.getOfficialIdentifier()); item.put("lawName", value.getLawName());
                item.put("articleReference", value.getArticleReference()); item.put("title", value.getTitle());
                item.put("officialSourceUri", value.getOfficialSourceUri()); item.put("effectiveDate", value.getEffectiveDate());
                item.put("retrievedAt", value.getRetrievedAt().toString()); item.put("contentHash", value.getContentHash());
                evidenceKeys.add(value.getOfficialSourceUri() + "|" + value.getArticleReference() + "|" + value.getContentHash());
            });
        ArrayNode deltas = result.putArray("deltaLegalReviews");
        decisions.stream().filter(value -> value.getLegalReviewResultJson() != null)
            .sorted(Comparator.comparing(value -> value.getHypothesisType().ordinal())).forEach(value -> {
            JsonNode delta = mapper.readTree(value.getLegalReviewResultJson());
            ObjectNode item = deltas.addObject(); item.put("hypothesisType", value.getHypothesisType().name());
            item.put("status", value.getLegalReviewStatus().name());
            item.set("result", delta);
            for (JsonNode reference : delta.path("officialEvidenceReferences")) {
                String key = reference.path("officialSourceUri").asText() + "|"
                    + reference.path("articleReference").asText() + "|" + reference.path("contentHash").asText();
                if (evidenceKeys.add(key)) evidence.add(reference.deepCopy());
            }
        });
        return result;
    }

    private JsonNode semantics(JsonNode candidate, String field) {
        for (JsonNode value : candidate.path("valueSemantics"))
            if (field.equals(value.path("fieldKey").asText())) return value;
        return mapper.createObjectNode();
    }
    private void copy(JsonNode from, ObjectNode to, String... keys) {
        for (String key : keys) if (from.has(key)) to.set(key, from.path(key).deepCopy());
    }
    private void copyAs(JsonNode from, ObjectNode to, String source, String target) {
        to.set(target, from.has(source) ? from.path(source).deepCopy() : mapper.createArrayNode());
    }
}
