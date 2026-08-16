package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptLegalRegulatoryReportRepository;
import com.aivle.backend.pipeline.market.*;
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
    private final ConceptLegalRegulatoryReportRepository legalReports;
    private final ObjectMapper mapper;

    public ConceptRefinementMaterialFactory(MarketResearchVersionRepository marketVersions,
            ConceptPortfolioConceptRepository concepts,
            ConceptLegalRegulatoryReportRepository legalReports, ObjectMapper mapper) {
        this.marketVersions = marketVersions;
        this.concepts = concepts;
        this.legalReports = legalReports;
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
        material.set("legalFindings", legalFindings(selection, concept));
        material.set("driftRejections", mapper.createArrayNode());
        material.set("userDeclined", mapper.createArrayNode());
        return input;
    }

    private MarketResearchVersion exact(Long projectId, Long id, MarketResearchRun.Kind kind) {
        return marketVersions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(id, projectId, kind)
            .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "사업 검증 결과 lineage를 찾을 수 없습니다."));
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

    private ArrayNode legalFindings(ConceptPortfolioSelection selection, ConceptPortfolioConcept concept) {
        ArrayNode out = mapper.createArrayNode();
        legalReports.findBySelectionIdAndStatusAndDeletedAtIsNull(selection.getId(), "CURRENT")
            .ifPresentOrElse(report -> out.add(mapper.readTree(report.getReportJson())),
                () -> out.add(mapper.readTree(concept.getLegalReviewJson())));
        return out;
    }

    private static void append(ArrayNode target, JsonNode values) {
        if (values.isArray()) values.forEach(value -> target.add(value.deepCopy()));
    }

    private static void putNullable(ObjectNode target, String name, Integer value) {
        if (value == null) target.putNull(name); else target.put(name, value);
    }
}
