package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioHypothesisDecision;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.PortfolioHypothesisType;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioHypothesisDecisionRepository;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Canonical, immutable hypothesis state captured immediately before refinement application. */
@Component
public class ConceptRefinementApplicationBeforeContract {
    static final String CONTRACT = "concept-refinement-application-before-v1";
    static final String SCHEMA_VERSION = "1.0";

    private final ConceptPortfolioHypothesisDecisionRepository hypotheses;
    private final ConceptRefinementDecisionContract decisions;
    private final ConceptPortfolioJsonHasher hasher;
    private final ObjectMapper mapper;

    public ConceptRefinementApplicationBeforeContract(
            ConceptPortfolioHypothesisDecisionRepository hypotheses,
            ConceptRefinementDecisionContract decisions,
            ConceptPortfolioJsonHasher hasher, ObjectMapper mapper) {
        this.hypotheses = hypotheses;
        this.decisions = decisions;
        this.hasher = hasher;
        this.mapper = mapper;
    }

    public Snapshot capture(ConceptRefinementRound round) {
        Set<PortfolioHypothesisType> selected = selectedTypes(round);
        ObjectNode root = mapper.createObjectNode();
        root.put("contract", CONTRACT);
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("roundId", round.getId());
        root.put("selectionId", round.getSelectionId());
        root.put("baselineSelectionRevision", round.baselineSelectionRevision());
        root.put("baselineBmPlanRevision", round.baselineBmPlanRevision());
        ArrayNode values = root.putArray("hypotheses");
        selected.stream().sorted(Comparator.comparing(Enum::name)).forEach(type -> {
            ConceptPortfolioHypothesisDecision value = hypotheses
                .findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
                    round.getSelectionId(), type)
                .orElseThrow(this::stale);
            if (!value.ready()) throw stale();
            values.add(toJson(value));
        });
        String json = mapper.writeValueAsString(root);
        return new Snapshot(root, json, hasher.hash(root), parseHypotheses(values));
    }

    public Snapshot validate(ConceptRefinementRound round) {
        if (round.getApplicationBeforeJson() == null || round.getApplicationBeforeHash() == null)
            throw stale();
        JsonNode parsed = mapper.readTree(round.getApplicationBeforeJson());
        if (!parsed.isObject() || !Objects.equals(hasher.hash(parsed), round.getApplicationBeforeHash()))
            throw stale();
        ObjectNode root = (ObjectNode) parsed;
        if (!CONTRACT.equals(root.path("contract").asText())
                || !SCHEMA_VERSION.equals(root.path("schemaVersion").asText())
                || root.path("roundId").asLong(-1) != round.getId()
                || root.path("selectionId").asLong(-1) != round.getSelectionId()
                || root.path("baselineSelectionRevision").asInt(-1) != round.baselineSelectionRevision()
                || root.path("baselineBmPlanRevision").asInt(-1) != round.baselineBmPlanRevision()
                || !root.path("hypotheses").isArray()) throw stale();
        List<BeforeHypothesis> before = parseHypotheses((ArrayNode) root.path("hypotheses"));
        Set<PortfolioHypothesisType> actual = EnumSet.noneOf(PortfolioHypothesisType.class);
        before.forEach(value -> {
            if (!actual.add(value.type())) throw stale();
        });
        if (!actual.equals(selectedTypes(round))) throw stale();
        return new Snapshot(root, round.getApplicationBeforeJson(), round.getApplicationBeforeHash(), before);
    }

    public boolean available(ConceptRefinementRound round) {
        try { validate(round); return true; }
        catch (RuntimeException invalid) { return false; }
    }

    private ObjectNode toJson(ConceptPortfolioHypothesisDecision value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("hypothesisType", value.getHypothesisType().name());
        node.put("conceptId", value.getConceptId());
        node.put("proposedValueJson", value.getProposedValueJson());
        textOrNull(node, "finalValueJson", value.getFinalValueJson());
        node.put("source", value.getSource());
        node.put("decisionStatus", value.getDecisionStatus());
        node.put("proposalVersion", value.getProposalVersion());
        node.put("locked", value.isLocked());
        node.put("semanticStatus", value.getSemanticStatus());
        textOrNull(node, "semanticReason", value.getSemanticReason());
        node.put("legalImpact", value.getLegalImpact());
        node.put("legalReviewStatus", value.getLegalReviewStatus());
        node.put("deltaLegalRequired", value.isDeltaLegalRequired());
        if (value.getDecidedByUserId() == null) node.putNull("decidedByUserId");
        else node.put("decidedByUserId", value.getDecidedByUserId());
        if (value.getDecidedAt() == null) node.putNull("decidedAt");
        else node.put("decidedAt", value.getDecidedAt().toString());
        return node;
    }

    private List<BeforeHypothesis> parseHypotheses(ArrayNode values) {
        List<BeforeHypothesis> result = new ArrayList<>();
        for (JsonNode value : values) {
            try {
                PortfolioHypothesisType type = PortfolioHypothesisType.valueOf(required(value, "hypothesisType"));
                String conceptId = required(value, "conceptId");
                String proposed = required(value, "proposedValueJson");
                String finalValue = nullable(value, "finalValueJson");
                String source = required(value, "source");
                String decision = required(value, "decisionStatus");
                int version = value.path("proposalVersion").asInt(-1);
                String semantic = required(value, "semanticStatus");
                String legalImpact = required(value, "legalImpact");
                String legalReview = required(value, "legalReviewStatus");
                if (version < 1 || finalValue == null) throw stale();
                Long userId = value.path("decidedByUserId").isNumber()
                    ? value.path("decidedByUserId").asLong() : null;
                java.time.Instant decidedAt = value.path("decidedAt").isTextual()
                    ? java.time.Instant.parse(value.path("decidedAt").asText()) : null;
                result.add(new BeforeHypothesis(type, conceptId, proposed, finalValue, source, decision,
                    version, value.path("locked").asBoolean(), semantic, nullable(value, "semanticReason"),
                    legalImpact, legalReview, value.path("deltaLegalRequired").asBoolean(), userId, decidedAt));
            } catch (BusinessException error) { throw error; }
            catch (RuntimeException invalid) { throw stale(); }
        }
        return List.copyOf(result);
    }

    private Set<PortfolioHypothesisType> selectedTypes(ConceptRefinementRound round) {
        JsonNode values = decisions.applicationPlan(round).path("hypotheses");
        if (!values.isObject()) throw stale();
        Set<PortfolioHypothesisType> selected = EnumSet.noneOf(PortfolioHypothesisType.class);
        try { for (String name : values.propertyNames()) selected.add(PortfolioHypothesisType.valueOf(name)); }
        catch (IllegalArgumentException invalid) { throw stale(); }
        if (selected.isEmpty()) throw stale();
        return selected;
    }

    private String required(JsonNode value, String field) {
        String result = nullable(value, field);
        if (result == null || result.isBlank()) throw stale();
        return result;
    }
    private String nullable(JsonNode value, String field) {
        JsonNode node = value.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
    private void textOrNull(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }
    private BusinessException stale() { return new BusinessException(ErrorCode.MODULE_INPUT_STALE); }

    public record Snapshot(ObjectNode value, String json, String hash, List<BeforeHypothesis> hypotheses) { }
    public record BeforeHypothesis(PortfolioHypothesisType type, String conceptId,
        String proposedValueJson, String finalValueJson, String source, String decisionStatus,
        int proposalVersion, boolean locked, String semanticStatus, String semanticReason,
        String legalImpact, String legalReviewStatus, boolean deltaLegalRequired,
        Long decidedByUserId, java.time.Instant decidedAt) { }
}
