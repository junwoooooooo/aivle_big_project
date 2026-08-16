package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.PortfolioHypothesisType;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Canonical proposal identity and immutable decision snapshot contract. */
@Component
public class ConceptRefinementDecisionContract {
    private static final Map<String, PortfolioHypothesisType> HYPOTHESIS_FIELDS = Map.of(
        "price", PortfolioHypothesisType.PRICE,
        "channels", PortfolioHypothesisType.CHANNELS,
        "differentiators", PortfolioHypothesisType.DIFFERENTIATORS,
        "targetRegion", PortfolioHypothesisType.TARGET_REGION,
        "revenueModel", PortfolioHypothesisType.REVENUE_MODEL,
        "preMarketSomShare", PortfolioHypothesisType.PRE_MARKET_SOM_SHARE,
        "preMarketSom", PortfolioHypothesisType.PRE_MARKET_SOM);
    private static final Map<String, String> BM_FIELDS = Map.of(
        "keyActivities", "key_activities",
        "keyResources", "key_resources",
        "keyPartners", "key_partners",
        "customerRelationships", "customer_relationship");
    private static final Set<String> BM_LIST_FIELDS = Set.of(
        "keyActivities", "keyResources", "keyPartners");
    private static final Set<String> OVERLAY_FIELDS = Set.of("targetUsers", "featureSet");

    private final ObjectMapper mapper;
    private final ConceptPortfolioJsonHasher hasher;

    public ConceptRefinementDecisionContract(ObjectMapper mapper, ConceptPortfolioJsonHasher hasher) {
        this.mapper = mapper;
        this.hasher = hasher;
    }

    public ProposalSet proposalSet(ConceptRefinementRound round) {
        JsonNode stored = readArray(round.getProposalJson());
        ObjectNode roundIdentity = roundIdentity(round);
        List<ProposalEntry> entries = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (JsonNode proposal : stored) {
            if (!validProposal(proposal)) throw staleContract();
            ObjectNode identity = mapper.createObjectNode();
            identity.set("round", roundIdentity.deepCopy());
            identity.put("fieldKey", proposal.path("fieldKey").asText());
            identity.set("currentValue", proposal.get("currentValue").deepCopy());
            identity.set("proposedValue", proposal.get("proposedValue").deepCopy());
            identity.put("source", proposal.path("source").asText());
            ArrayNode evidence = identity.putArray("evidenceIds");
            normalizedEvidence(proposal.path("evidenceIds")).forEach(evidence::add);
            if (proposal.hasNonNull("legalRef")) identity.put("legalRef", proposal.path("legalRef").asText());
            else identity.putNull("legalRef");
            String key = hasher.hash(identity);
            if (!keys.add(key)) throw staleContract();
            ObjectNode projected = (ObjectNode) proposal.deepCopy();
            projected.put("proposalKey", key);
            entries.add(new ProposalEntry(key, projected));
        }
        entries.sort(Comparator.comparing(ProposalEntry::key));
        ArrayNode projected = mapper.createArrayNode();
        ArrayNode orderedKeys = mapper.createArrayNode();
        Map<String, ObjectNode> byKey = new LinkedHashMap<>();
        for (ProposalEntry entry : entries) {
            projected.add(entry.proposal());
            orderedKeys.add(entry.key());
            byKey.put(entry.key(), entry.proposal());
        }
        ObjectNode setIdentity = mapper.createObjectNode();
        setIdentity.put("contract", "concept-refinement-proposal-set-v1");
        setIdentity.set("round", roundIdentity);
        setIdentity.set("proposalKeys", orderedKeys);
        return new ProposalSet(hasher.hash(setIdentity), projected,
            Collections.unmodifiableMap(byKey), List.copyOf(byKey.keySet()));
    }

    public DecisionMaterial decision(ConceptRefinementRound round, ProposalSet proposalSet,
            List<String> requestedKeys, boolean keepCurrent) {
        List<String> selectedKeys = canonicalSelectedKeys(requestedKeys);
        if (keepCurrent == !selectedKeys.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "제안 선택과 현재 사업안 유지 중 하나만 선택해 주세요.");
        }
        for (String key : selectedKeys) {
            if (!proposalSet.byKey().containsKey(key)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "현재 제안 목록에 없는 proposalKey입니다.");
            }
        }

        ObjectNode plan = mapper.createObjectNode();
        ObjectNode hypotheses = plan.putObject("hypotheses");
        ObjectNode bmPlan = plan.putObject("bmPlan");
        ObjectNode overlay = plan.putObject("overlay");
        Set<String> selectedFields = new HashSet<>();
        ArrayNode selected = mapper.createArrayNode();
        for (String key : selectedKeys) {
            ObjectNode proposal = proposalSet.byKey().get(key);
            String field = proposal.path("fieldKey").asText();
            if (!selectedFields.add(field)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "같은 fieldKey의 제안은 하나만 선택할 수 있습니다.");
            }
            JsonNode value = proposal.get("proposedValue").deepCopy();
            PortfolioHypothesisType hypothesis = HYPOTHESIS_FIELDS.get(field);
            if (hypothesis != null) hypotheses.set(hypothesis.name(), value);
            else if (BM_FIELDS.containsKey(field)) bmPlan.set(BM_FIELDS.get(field), value);
            else if (OVERLAY_FIELDS.contains(field)) overlay.set(field, value);
            else throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "적용 경로가 없는 refinement field입니다.");
            selected.add(proposal.deepCopy());
        }

        Set<String> selectedSet = new HashSet<>(selectedKeys);
        ArrayNode declined = mapper.createArrayNode();
        proposalSet.orderedKeys().stream().filter(key -> !selectedSet.contains(key)).forEach(declined::add);
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.put("contract", "concept-refinement-decision-v1");
        snapshot.set("source", source(round));
        snapshot.put("proposalSetHash", proposalSet.hash());
        snapshot.put("keepCurrent", keepCurrent);
        ArrayNode selectedKeyJson = snapshot.putArray("selectedProposalKeys");
        selectedKeys.forEach(selectedKeyJson::add);
        snapshot.set("selectedProposals", selected);
        snapshot.set("declinedProposalKeys", declined);
        snapshot.set("plan", plan);
        return new DecisionMaterial(snapshot, hasher.hash(snapshot), selectedKeys,
            declined.size(), new PlanSummary(hypotheses.size(), bmPlan.size(), overlay.size()));
    }

    public DecisionView decisionView(ConceptRefinementRound round) {
        if (round.getDecisionJson() == null) return null;
        JsonNode decision = mapper.readTree(round.getDecisionJson());
        if (decision == null || !decision.isObject()) throw staleContract();
        List<String> selected = new ArrayList<>();
        decision.path("selectedProposalKeys").forEach(value -> selected.add(value.asText()));
        JsonNode plan = decision.path("plan");
        return new DecisionView(round.getState().name(), round.getDecisionHash(), List.copyOf(selected), selected.size(),
            decision.path("declinedProposalKeys").size(),
            new PlanSummary(plan.path("hypotheses").size(), plan.path("bmPlan").size(),
                plan.path("overlay").size()));
    }

    public ObjectNode applicationPlan(ConceptRefinementRound round) {
        ObjectNode decision = decisionSnapshot(round);
        JsonNode plan = decision.path("plan");
        if (!plan.isObject() || !plan.path("hypotheses").isObject()
                || !plan.path("bmPlan").isObject() || !plan.path("overlay").isObject()) {
            throw staleContract();
        }
        return (ObjectNode) plan.deepCopy();
    }

    public ObjectNode rollbackBmPatch(ConceptRefinementRound round) {
        ObjectNode decision = decisionSnapshot(round);
        JsonNode selected = decision.path("selectedProposals");
        if (!selected.isArray()) throw staleContract();
        ObjectNode patch = mapper.createObjectNode();
        for (JsonNode proposal : selected) {
            String field = proposal.path("fieldKey").asText();
            String storageField = BM_FIELDS.get(field);
            if (storageField == null) continue;
            if (!proposal.has("currentValue")) throw staleContract();
            JsonNode current = proposal.get("currentValue");
            if (BM_LIST_FIELDS.contains(field)) {
                if (current.isNull()) patch.set(storageField, mapper.createArrayNode());
                else {
                    if (!current.isArray()) throw staleContract();
                    for (JsonNode value : current) if (!value.isTextual()) throw staleContract();
                    patch.set(storageField, current.deepCopy());
                }
            } else {
                if (current.isNull()) patch.put(storageField, "");
                else {
                    if (!current.isTextual()) throw staleContract();
                    patch.set(storageField, current.deepCopy());
                }
            }
        }
        return patch;
    }

    public String applicationHash(ConceptRefinementRound round) {
        ObjectNode identity = mapper.createObjectNode();
        identity.put("contract", "concept-refinement-application-v1");
        identity.set("round", roundIdentity(round));
        identity.put("decisionHash", round.getDecisionHash());
        return hasher.hash(identity);
    }

    private ObjectNode decisionSnapshot(ConceptRefinementRound round) {
        if (round.getDecisionJson() == null || round.getDecisionHash() == null) throw staleContract();
        JsonNode decision = mapper.readTree(round.getDecisionJson());
        if (decision == null || !decision.isObject() || !hasher.hash(decision).equals(round.getDecisionHash()))
            throw staleContract();
        return (ObjectNode) decision;
    }

    private List<String> canonicalSelectedKeys(List<String> requestedKeys) {
        List<String> values = requestedKeys == null ? List.of() : requestedKeys;
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "proposalKey가 올바르지 않습니다.");
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "중복 proposalKey는 허용되지 않습니다.");
        }
        return values.stream().sorted().toList();
    }

    private boolean validProposal(JsonNode proposal) {
        return proposal != null && proposal.isObject()
            && !proposal.path("fieldKey").asText().isBlank()
            && proposal.has("currentValue") && proposal.has("proposedValue")
            && !proposal.get("proposedValue").isNull()
            && !proposal.path("source").asText().isBlank()
            && (!proposal.has("evidenceIds") || proposal.path("evidenceIds").isArray());
    }

    private List<String> normalizedEvidence(JsonNode evidenceIds) {
        if (!evidenceIds.isArray()) return List.of();
        Set<String> values = new TreeSet<>();
        evidenceIds.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText());
        });
        return List.copyOf(values);
    }

    private JsonNode readArray(String json) {
        if (json == null) return mapper.createArrayNode();
        JsonNode value = mapper.readTree(json);
        if (value == null || !value.isArray()) throw staleContract();
        return value;
    }

    private ObjectNode source(ConceptRefinementRound round) {
        ObjectNode source = mapper.createObjectNode();
        source.put("businessValidationSessionId", round.getBusinessValidationSessionId());
        source.put("marketVersionId", round.getSourceMarketVersionId());
        source.put("bmVersionId", round.getSourceBmVersionId());
        source.put("marketSeedSnapshotId", round.getSourceMarketSeedSnapshotId());
        source.put("selectionId", round.getSelectionId());
        source.put("selectionRevision", round.getSourceSelectionRevision());
        source.put("bmPlanRevision", round.getSourceBmPlanRevision());
        return source;
    }

    private ObjectNode roundIdentity(ConceptRefinementRound round) {
        ObjectNode identity = source(round);
        identity.put("round", round.getRoundNumber());
        return identity;
    }

    private BusinessException staleContract() {
        return new BusinessException(ErrorCode.MODULE_INPUT_STALE,
            "저장된 refinement proposal 계약이 올바르지 않습니다.");
    }

    private record ProposalEntry(String key, ObjectNode proposal) { }
    public record ProposalSet(String hash, ArrayNode projected,
                              Map<String, ObjectNode> byKey, List<String> orderedKeys) { }
    public record DecisionMaterial(ObjectNode snapshot, String hash,
                                   List<String> selectedKeys, int declinedCount,
                                   PlanSummary planSummary) { }
    public record PlanSummary(int hypotheses, int bmPlan, int overlay) { }
    public record DecisionView(String state, String decisionHash, List<String> selectedProposalKeys,
                               int selectedCount, int declinedCount,
                               PlanSummary planSummary) { }
}
