package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.market.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.*;

/** Builds one canonical proposal input from the exact validation versions pinned by the session. */
@Component
public class ConceptRefinementMaterialFactory {
    private static final int EVIDENCE_LIMIT = 200;
    private final MarketResearchVersionRepository marketVersions;
    private final ConceptPortfolioConceptRepository concepts;
    private final MarketAnalysisSeedSnapshotRepository seeds;
    private final BmPlanPreparationService bmPlans;
    private final ObjectMapper mapper;

    public ConceptRefinementMaterialFactory(MarketResearchVersionRepository marketVersions,
            ConceptPortfolioConceptRepository concepts,
            MarketAnalysisSeedSnapshotRepository seeds, BmPlanPreparationService bmPlans,
            ObjectMapper mapper) {
        this.marketVersions = marketVersions;
        this.concepts = concepts;
        this.seeds = seeds;
        this.bmPlans = bmPlans;
        this.mapper = mapper;
    }

    public ObjectNode input(Long projectId, ConceptPortfolioSelection selection,
            CompletedSource source, int attempt) {
        MarketResearchVersion market = exact(projectId, source.marketVersionId(), MarketResearchRun.Kind.FULL);
        MarketResearchVersion bm = exact(projectId, source.bmVersionId(), MarketResearchRun.Kind.BM);
        ConceptPortfolioConcept concept = concepts
            .findByIdAndProjectIdAndDeletedAtIsNull(selection.getConceptId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        JsonNode marketResult = mapper.readTree(market.getResultJson());
        JsonNode bmResult = mapper.readTree(bm.getResultJson());
        JsonNode selectedCandidate = mapper.readTree(concept.getCandidateSnapshotJson());
        JsonNode sourceSeed = exactSeed(projectId, source);
        BmPlanPreparationService.PlanView bmPlan = bmPlans.current(projectId);
        if (!Objects.equals(bmPlan.revision(), source.bmPlanRevision())) {
            throw stale("BM Plan revision이 사업 검증 source와 다릅니다.");
        }

        ObjectNode input = mapper.createObjectNode();
        input.put("action", "REFINE_FROM_MARKET");
        input.put("expectedHypothesisRevision", selection.getHypothesisRevision());
        input.set("selectedCandidate", selectedCandidate.deepCopy());
        input.set("baseLegalReview", mapper.readTree(concept.getLegalReviewJson()));

        ObjectNode material = input.putObject("refinementMaterial");
        material.put("round", 1);
        material.put("attempt", attempt);
        material.put("policyVersion", ConceptRefinementPolicy.VERSION);
        material.put("maxProposals", ConceptRefinementPolicy.MAX_PROPOSALS);
        material.put("priceTolerance", ConceptRefinementPolicy.PRICE_TOLERANCE);
        material.put("listChangeAllowance", ConceptRefinementPolicy.LIST_CHANGE_ALLOWANCE);
        ObjectNode binding = material.putObject("sourceBinding");
        binding.put("businessValidationSessionId", source.businessValidationSessionId());
        binding.put("marketVersionId", source.marketVersionId());
        binding.put("bmVersionId", source.bmVersionId());
        binding.put("marketSeedSnapshotId", source.marketSeedSnapshotId());
        binding.put("selectionId", source.selectionId());
        putNullable(binding, "selectionRevision", source.selectionRevision());
        putNullable(binding, "bmPlanRevision", source.bmPlanRevision());

        ArrayNode frozen = material.putArray("frozenFields");
        ConceptRefinementPolicy.FROZEN_FIELDS.stream().sorted().forEach(frozen::add);
        ObjectNode refinable = material.putObject("refinableFields");
        ConceptRefinementPolicy.REFINABLE_FIELDS.entrySet().stream()
            .sorted(Map.Entry.comparingByKey()).forEach(entry -> refinable.put(entry.getKey(), entry.getValue()));
        ConceptRefinementPolicy.FREE_WITH_EVIDENCE_FIELDS.forEach(value -> refinable.put(value, "FREE_WITH_EVIDENCE"));
        ConceptRefinementPolicy.FREE_BM_FIELDS.forEach(value -> refinable.put(value, "FREE_BM"));

        material.set("gateReasons", gateReasons(bmResult));
        material.set("canvas", bmResult.path("canvas").deepCopy());
        material.set("marketEvidence", evidence(marketResult.path("evidence"), bmResult.path("canvas")));
        material.set("currentEditableValues", currentEditableValues(sourceSeed, bmPlan.plan()));
        material.set("frozenValues", frozenValues(sourceSeed.path("selectedConcept")));
        material.set("legalFindings", legalFindings(sourceSeed.path("legalResult")));
        material.set("allowedLegalRefs", allowedLegalRefs(sourceSeed.path("legalResult")));
        material.set("driftRejections", mapper.createArrayNode());
        material.set("userDeclined", mapper.createArrayNode());
        return input;
    }

    private MarketResearchVersion exact(Long projectId, Long id, MarketResearchRun.Kind kind) {
        return marketVersions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(id, projectId, kind)
            .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "사업 검증 결과 lineage를 찾을 수 없습니다."));
    }

    private JsonNode exactSeed(Long projectId, CompletedSource source) {
        MarketAnalysisSeedSnapshot seed = seeds
            .findByIdAndStaleAtIsNullAndDeletedAtIsNull(source.marketSeedSnapshotId())
            .filter(value -> Objects.equals(value.getId(), source.marketSeedSnapshotId())
                && Objects.equals(value.getProjectId(), projectId)
                && Objects.equals(value.getPortfolioSelectionId(), source.selectionId())
                && "CONCEPT_PORTFOLIO_V2".equals(value.getSourceType()))
            .orElseThrow(() -> stale("사업 검증의 Market Seed lineage가 현재 유효하지 않습니다."));
        JsonNode snapshot;
        try { snapshot = mapper.readTree(seed.getSnapshotJson()); }
        catch (RuntimeException invalid) { throw stale("Market Seed 계약을 읽을 수 없습니다."); }
        if (snapshot == null || !snapshot.isObject()
                || !"market-analysis-seed-snapshot-v1".equals(snapshot.path("contract").asText())
                || !"2.0".equals(snapshot.path("schemaVersion").asText())
                || !snapshot.path("selectedConcept").isObject()
                || !snapshot.path("finalHypotheses").isObject()
                || !snapshot.path("legalResult").isObject()) {
            throw stale("Market Seed 계약이 사업 검증 baseline 요구사항과 다릅니다.");
        }
        return snapshot;
    }

    private ObjectNode currentEditableValues(JsonNode seed, ObjectNode plan) {
        ObjectNode out = mapper.createObjectNode();
        JsonNode hypotheses = seed.path("finalHypotheses");
        for (String field : List.of("targetRegion", "revenueModel", "price", "channels",
                "differentiators", "preMarketSomShare", "preMarketSom")) {
            JsonNode value = hypotheses.path(field).get("value");
            if (value == null || value.isNull()) throw stale("확정 가설 baseline이 불완전합니다: " + field);
            out.set(field, value.deepCopy());
        }
        out.set("targetUsers", required(seed.path("selectedConcept").path("identity"), "targetUsers"));
        out.set("featureSet", required(seed.path("selectedConcept").path("solution"), "featureSet"));
        Map<String, String> bmFields = new LinkedHashMap<>();
        bmFields.put("keyActivities", "key_activities");
        bmFields.put("keyResources", "key_resources");
        bmFields.put("keyPartners", "key_partners");
        bmFields.put("customerRelationships", "customer_relationship");
        bmFields.forEach((external, internal) -> out.set(external,
            plan.has(internal) ? plan.path(internal).deepCopy() : NullNode.getInstance()));
        return out;
    }

    private ObjectNode frozenValues(JsonNode selectedConcept) {
        ObjectNode out = mapper.createObjectNode();
        List<JsonNode> sections = List.of(selectedConcept.path("identity"),
            selectedConcept.path("solution"), selectedConcept.path("operation"));
        ConceptRefinementPolicy.FROZEN_FIELDS.stream().sorted().forEach(field -> sections.stream()
            .map(section -> section.get(field)).filter(Objects::nonNull).filter(value -> !value.isNull())
            .findFirst().ifPresent(value -> out.set(field, value.deepCopy())));
        return out;
    }

    private ArrayNode gateReasons(JsonNode bmResult) {
        ArrayNode out = mapper.createArrayNode();
        append(out, bmResult.path("bm").path("weaknesses"));
        append(out, bmResult.path("bm").path("risks"));
        return out;
    }

    private ArrayNode evidence(JsonNode source, JsonNode canvas) {
        ArrayNode out = mapper.createArrayNode();
        if (!source.isArray()) return out;
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        source.forEach(item -> {
            String id = item.path("id").asText("");
            if (!id.isBlank()) byId.putIfAbsent(id, item);
        });
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        JsonNode cells = canvas.path("cells");
        if (cells.isArray()) cells.forEach(cell -> cell.path("marketEvidenceIds")
            .forEach(id -> ordered.add(id.asText())));
        ordered.addAll(byId.keySet());
        ordered.stream().limit(EVIDENCE_LIMIT).map(byId::get).filter(Objects::nonNull)
            .forEach(item -> out.add(item.deepCopy()));
        return out;
    }

    private ArrayNode legalFindings(JsonNode legalResult) {
        ArrayNode out = mapper.createArrayNode();
        out.add(legalResult.deepCopy());
        return out;
    }

    private ArrayNode allowedLegalRefs(JsonNode legalResult) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        JsonNode references = legalResult.path("officialEvidenceReferences");
        if (references.isArray()) references.forEach(reference -> {
            add(values, reference.path("articleReference").asText(""));
            add(values, reference.path("officialIdentifier").asText(""));
            add(values, reference.path("title").asText(""));
            String law = reference.path("lawName").asText("").strip();
            String article = reference.path("articleReference").asText("").strip();
            if (!law.isBlank() && !article.isBlank()) add(values, law + " " + article);
        });
        ArrayNode out = mapper.createArrayNode();
        values.forEach(out::add);
        return out;
    }

    private JsonNode required(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) throw stale("Market Seed baseline이 불완전합니다: " + field);
        return value.deepCopy();
    }

    private static void add(Set<String> target, String value) {
        if (value != null && !value.isBlank()) target.add(value.strip());
    }

    private static BusinessException stale(String message) {
        return new BusinessException(ErrorCode.MODULE_INPUT_STALE, message);
    }

    private static void append(ArrayNode target, JsonNode values) {
        if (values.isArray()) values.forEach(value -> target.add(value.deepCopy()));
    }

    private static void putNullable(ObjectNode target, String name, Integer value) {
        if (value == null) target.putNull(name); else target.put(name, value);
    }
}
