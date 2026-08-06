package com.aivle.backend.pipeline.planning.application;

import com.aivle.backend.pipeline.integration.domain.PlanningChangeProposal;
import com.aivle.backend.pipeline.integration.domain.ProposalDecisionStatus;
import com.aivle.backend.pipeline.planning.domain.PlanningChangeDecision;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;

@Component
public class DeterministicPlanningPatch {
    private final ObjectMapper mapper;
    public DeterministicPlanningPatch(ObjectMapper mapper) { this.mapper = mapper; }

    public JsonNode apply(JsonNode originalConcept, List<PlanningChangeProposal> proposals,
            Map<String, PlanningChangeDecision> decisions) {
        ObjectNode result = originalConcept instanceof ObjectNode object ? object.deepCopy() : mapper.createObjectNode();
        proposals.stream().sorted(Comparator.comparing(PlanningChangeProposal::getId)).forEach(proposal -> {
            PlanningChangeDecision decision = decisions.get(proposal.getId());
            if (decision == null || decision.getDecision() == ProposalDecisionStatus.REJECT) return;
            JsonNode selected = decision.getDecision() == ProposalDecisionStatus.PARTIALLY_ADOPT
                ? mapper.readTree(decision.getAppliedValueJson()) : mapper.readTree(proposal.getAfterJson());
            List<String> fields = mapper.readValue(proposal.getAffectedFieldsJson(),
                new tools.jackson.core.type.TypeReference<List<String>>() {});
            for (String field : fields) setPath(result, field, selected, fields.size());
        });
        return result;
    }

    private void setPath(ObjectNode root, String dottedPath, JsonNode selected, int fieldCount) {
        String[] parts = dottedPath.split("\\."); ObjectNode cursor = root;
        for (int i=0; i<parts.length-1; i++) {
            JsonNode child = cursor.get(parts[i]);
            if (!(child instanceof ObjectNode)) { child = mapper.createObjectNode(); cursor.set(parts[i], child); }
            cursor = (ObjectNode) child;
        }
        String leaf = parts[parts.length-1];
        JsonNode value = selected.isObject() && selected.has(leaf) ? selected.get(leaf) : selected;
        cursor.set(leaf, value.deepCopy());
    }
}
