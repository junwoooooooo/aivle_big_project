package com.aivle.backend.pipeline.launchreadiness.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Launch authority is the current selected concept, independent of Market and BM execution. */
@Component
@RequiredArgsConstructor
public class LaunchReadinessConceptSourceResolver {
    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioConceptRepository concepts;
    private final ObjectMapper mapper;

    public Source require(Long projectId, String userMessage) {
        Source source = currentOrNull(projectId);
        if (source == null) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE, userMessage);
        return source;
    }

    public Source currentOrNull(Long projectId) {
        ConceptPortfolioSelection selection = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        if (selection == null) return null;
        ConceptPortfolioConcept concept = concepts
            .findByIdAndProjectIdAndDeletedAtIsNull(selection.getConceptId(), projectId)
            .filter(value -> value.getRun().getId().equals(selection.getRunId()))
            .filter(value -> value.getCandidateId().equals(selection.getCandidateId()))
            .filter(value -> value.getCanonicalHash().equals(selection.getSelectedConceptHash()))
            .orElse(null);
        if (concept == null) return null;
        JsonNode snapshot = mapper.readTree(concept.getCandidateSnapshotJson());
        JsonNode candidate = snapshot.path("candidate");
        if (!candidate.isObject()) candidate = snapshot;
        if (!candidate.isObject() || candidate.isEmpty()) return null;
        ObjectNode currentConcept = (ObjectNode) candidate.deepCopy();
        if (!currentConcept.has("conceptName")) currentConcept.put("conceptName", concept.getConceptName());
        currentConcept.put("conceptId", concept.getId());
        currentConcept.put("canonicalHash", selection.getSelectedConceptHash());
        return new Source(selection, concept, currentConcept);
    }

    public Binding binding(Source source) {
        if (source == null) return null;
        return new Binding(source.selection().getId(), source.selection().getHypothesisRevision(),
            source.concept().getId(), source.selection().getSelectedConceptHash());
    }

    public record Source(ConceptPortfolioSelection selection, ConceptPortfolioConcept concept,
                         ObjectNode currentConcept) { }
    public record Binding(Long selectionId, int selectionRevision, String conceptId,
                          String selectedConceptHash) { }
}
