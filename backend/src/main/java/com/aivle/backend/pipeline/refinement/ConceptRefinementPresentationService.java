package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Read-only MAIN presentation projection over the existing v3 refinement authority. */
@Service
@Transactional(readOnly = true)
public class ConceptRefinementPresentationService {
    private final ProjectRepository projects;
    private final ConceptRefinementRoundRepository rounds;
    private final ConceptRefinementFinalRepository finals;
    private final MarketAnalysisSeedSnapshotRepository seeds;
    private final ConceptRefinementDecisionContract decisions;
    private final ConceptRefinementService refinement;
    private final ObjectMapper mapper;

    public ConceptRefinementPresentationService(ProjectRepository projects,
            ConceptRefinementRoundRepository rounds, ConceptRefinementFinalRepository finals,
            MarketAnalysisSeedSnapshotRepository seeds, ConceptRefinementDecisionContract decisions,
            ConceptRefinementService refinement, ObjectMapper mapper) {
        this.projects=projects; this.rounds=rounds; this.finals=finals; this.seeds=seeds;
        this.decisions=decisions; this.refinement=refinement; this.mapper=mapper;
    }

    public JsonNode current(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        ConceptRefinementService.CurrentView current = refinement.current(ownerId, projectId);
        ObjectNode root = mapper.createObjectNode();
        root.put("sourceBusinessValidationSessionId", current.sourceBusinessValidationSessionId());
        root.put("outcome", outcome(current.state())); root.put("state", current.state());
        root.put("stale", current.stale()); root.put("finalized", "FINALIZED".equals(current.state()));
        ArrayNode historyNode = root.putArray("roundHistory"); ArrayNode changes = root.putArray("changes");
        ArrayNode unresolved = root.putArray("unresolved");
        List<ConceptRefinementRound> history = current.sourceBusinessValidationSessionId() == null ? List.of()
            : rounds.findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(
                projectId, current.sourceBusinessValidationSessionId());
        for (ConceptRefinementRound round : history) {
            ObjectNode roundView = historyNode.addObject(); roundView.put("round", round.getRoundNumber());
            roundView.put("state", round.getState().name());
            if (round.getProposalJson() == null) continue;
            ConceptRefinementDecisionContract.ProposalSet set = decisions.proposalSet(round);
            Set<String> selected = new HashSet<>(); boolean decided = round.getDecisionJson() != null;
            if (decided) decisions.decisionView(round).selectedProposalKeys().forEach(selected::add);
            boolean declined = round.getState() == ConceptRefinementRound.State.DECLINED;
            for (JsonNode proposal : set.projected()) {
                String key = proposal.path("proposalKey").asText(); ObjectNode change = changes.addObject();
                change.put("proposalKey", key); change.put("round", round.getRoundNumber());
                change.put("field", proposal.path("fieldKey").asText());
                change.put("title", text(proposal, "title", proposal.path("fieldKey").asText()));
                change.put("before", display(proposal.get("currentValue")));
                change.put("after", text(proposal, "afterText", display(proposal.get("proposedValue"))));
                change.put("afterValue", display(proposal.get("proposedValue")));
                change.put("reason", text(proposal, "rationale", ""));
                change.put("source", text(proposal, "source", ""));
                change.set("evidenceIds", normalizedArray(proposal.path("evidenceIds")));
                if (proposal.hasNonNull("legalRef")) change.put("legalRef", proposal.path("legalRef").asText());
                else change.putNull("legalRef");
                if (!decided && !declined) change.putNull("accepted"); else change.put("accepted", selected.contains(key));
                change.putNull("narrativeRef");
            }
            JsonNode rejected = readArray(round.getDriftRejectionsJson()); rejected.forEach(value -> unresolved.add(value.deepCopy()));
        }
        root.put("rounds", history.size());
        root.putNull("deltaLegal"); root.putArray("narrative");
        ObjectNode retry = root.putObject("retry");
        retry.put("failed", "FAILED".equals(current.state())); retry.put("reason", current.errorCode());
        retry.put("attemptsUsed", current.retry().attempts()); retry.put("maxAttempts", current.retry().maxAttempts());
        retry.put("retryable", current.retry().available());
        root.set("concept", concept(projectId, current.sourceBusinessValidationSessionId(), history));
        ObjectNode command = root.putObject("command"); command.put("round", current.round());
        if (current.proposalSetHash() == null) command.putNull("proposalSetHash"); else command.put("proposalSetHash", current.proposalSetHash());
        if (current.decision() == null || current.decision().decisionHash() == null) command.putNull("decisionHash");
        else command.put("decisionHash", current.decision().decisionHash());
        command.put("nextAvailable", current.nextRound().available()); command.put("maxRounds", current.nextRound().maxRounds());
        return root;
    }

    private JsonNode concept(Long projectId, String sessionId, List<ConceptRefinementRound> history) {
        ConceptRefinementFinal fin = finals.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        if (fin != null && java.util.Objects.equals(sessionId, fin.getSourceBusinessValidationSessionId())) {
            JsonNode value = mapper.readTree(fin.getFinalJson());
            if (value != null && value.path("selectedConcept").isObject()) {
                ObjectNode snapshot = mapper.createObjectNode(); snapshot.set("selectedConcept", value.path("selectedConcept").deepCopy());
                snapshot.set("finalHypotheses", value.path("finalHypotheses").deepCopy()); return snapshot; } }
        if (history.isEmpty()) return mapper.createObjectNode();
        String seedId = history.get(history.size() - 1).getSourceMarketSeedSnapshotId();
        return seeds.findByIdAndDeletedAtIsNull(seedId).map(seed -> mapper.readTree(seed.getSnapshotJson()))
            .orElseGet(mapper::createObjectNode);
    }

    private ArrayNode normalizedArray(JsonNode value) { ArrayNode result=mapper.createArrayNode();
        if(value!=null&&value.isArray())value.forEach(item->{if(item.isTextual()&&!item.asText().isBlank())result.add(item.asText());});return result; }
    private JsonNode readArray(String json) { if(json==null)return mapper.createArrayNode(); JsonNode value=mapper.readTree(json);return value!=null&&value.isArray()?value:mapper.createArrayNode(); }
    private String text(JsonNode node,String field,String fallback){String value=node.path(field).asText();return value.isBlank()?fallback:value;}
    private String display(JsonNode value){if(value==null||value.isNull())return "";if(value.isTextual())return value.asText();
        if(value.isArray()){List<String> parts=new ArrayList<>();value.forEach(item->{if(item.isValueNode())parts.add(item.asText());});return String.join(" · ",parts);}return value.toString();}
    private String outcome(String state){return switch(state){
        case "AWAITING_DECISION" -> "AWAITING_DECISION"; case "FAILED" -> "FAILED";
        case "DECLINED" -> "DECLINED"; case "LEGAL_BLOCKED" -> "LEGAL_BLOCKED";
        case "NO_CHANGES" -> "NOTHING_TO_FIX"; case "NOT_STARTED" -> "NOT_STARTED";
        case "FINALIZED" -> "CONVERGED";
        case "STALE" -> "FAILED"; default -> "RUNNING";};}
}
