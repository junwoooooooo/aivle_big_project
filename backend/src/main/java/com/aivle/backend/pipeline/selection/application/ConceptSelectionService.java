package com.aivle.backend.pipeline.selection.application;

import static com.aivle.backend.pipeline.selection.api.SelectionApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.application.ConceptLegalFactPatternMapper;
import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.repository.ConceptFactoryRunRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.pipeline.concept.worker.ConceptFactoryAiGateway;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalAssessmentRepository;
import com.aivle.backend.pipeline.selection.domain.*;
import com.aivle.backend.pipeline.selection.repository.ConceptHypothesisDecisionRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class ConceptSelectionService {
    private final ProjectRepository projects;
    private final ConceptFactoryRunRepository runs;
    private final ConceptRepository concepts;
    private final ConceptLegalAssessmentRepository assessments;
    private final ConceptSelectionRepository selections;
    private final ConceptHypothesisDecisionRepository decisions;
    private final ConceptLegalFactPatternMapper legalFactPatterns;
    private final ConceptFactoryAiGateway ai;
    private final ObjectMapper mapper;

    @Transactional
    public SelectionResponse select(Long ownerId, Long projectId, CreateSelectionRequest request) {
        projects.findByIdForUpdate(projectId)
            .filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        var run = runs.findCurrentOwned(ownerId, projectId)
            .filter(value -> value.getStatus() == ConceptFactoryRunStatus.COMPLETED)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        Concept concept = concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull(request.conceptId(), projectId)
            .filter(value -> value.getRun().getId().equals(run.getId())
                && value.getSourceSnapshotHash().equals(run.getSourceSnapshotHash()))
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        assessments.findByConceptIdAndProjectIdAndDeletedAtIsNull(concept.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        String reason = request.selectionReason().strip();
        String requestHash = SelectionRequestFingerprint.create(concept.getId(), concept.getCanonicalHash(), reason);
        var idempotent = selections.findByProjectIdAndRequestHashAndCurrentSelectionTrueAndDeletedAtIsNull(projectId, requestHash);
        if (idempotent.isPresent()) return response(idempotent.get());

        selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId).ifPresent(ConceptSelection::supersede);
        selections.flush();
        Instant now = Instant.now();
        ConceptSelection selection = selections.save(ConceptSelection.select(
            projectId, concept.getId(), reason, requestHash, ownerId, now));
        initializeDecisions(selection, concept, ownerId, now);
        return response(selection);
    }

    @Transactional(readOnly = true)
    public SelectionResponse current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        ConceptSelection selection = currentSelection(projectId);
        return response(selection);
    }

    @Transactional
    public HypothesisActionResponse decide(Long ownerId, Long projectId, String typeText,
            HypothesisActionRequest request) {
        requireOwned(ownerId, projectId);
        ConceptSelection selection = currentSelection(projectId);
        HypothesisType type;
        try {
            type = HypothesisType.valueOf(typeText);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.HYPOTHESIS_NOT_FOUND);
        }
        ConceptHypothesisDecision current = decisions
            .findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(selection.getId(), type)
            .orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_NOT_FOUND));
        if (current.getProposalVersion() != request.expectedProposalVersion()) {
            throw new BusinessException(ErrorCode.HYPOTHESIS_VERSION_CONFLICT);
        }
        if (current.isLocked()) throw new BusinessException(ErrorCode.HYPOTHESIS_LOCKED);
        Concept concept = concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull(selection.getConceptId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));

        ConceptHypothesisDecision result;
        if (request.action() == HypothesisAction.REQUEST_ALTERNATIVE) {
            current.reject();
            JsonNode alternative = alternative(concept, current);
            result = decisions.save(ConceptHypothesisDecision.alternative(
                current, mapper.writeValueAsString(alternative), ownerId));
        } else {
            JsonNode finalValue = request.action() == HypothesisAction.ACCEPT
                ? mapper.readTree(current.getProposedValueJson()) : request.value();
            validateValue(type, finalValue);
            boolean edited = request.action() == HypothesisAction.EDIT_AND_ACCEPT;
            JsonNode baseline = mapper.readTree(concept.getCandidateJson()).path(type.candidateField());
            boolean baselineChanged = !canonical(baseline).equals(canonical(finalValue));
            DeltaResult delta = type.legalSensitive() && baselineChanged
                ? deltaReview(concept, type, finalValue, edited) : DeltaResult.notRequired();
            current.accept(mapper.writeValueAsString(finalValue), edited, ownerId, Instant.now(),
                baselineChanged, delta.passed(), delta.resultJson());
            result = decisions.save(current);
        }
        return new HypothesisActionResponse(decisionResponse(result), allComplete(selection.getId()));
    }

    private void initializeDecisions(ConceptSelection selection, Concept concept, Long ownerId, Instant now) {
        JsonNode candidate = mapper.readTree(concept.getCandidateJson());
        Map<String, JsonNode> semantics = new java.util.HashMap<>();
        candidate.path("valueSemantics").forEach(item -> semantics.put(item.path("fieldKey").asText(), item));
        for (HypothesisType type : HypothesisType.values()) {
            JsonNode value = candidate.path(type.candidateField());
            validateValue(type, value);
            JsonNode semantic = semantics.get(type.candidateField());
            if (semantic == null) throw new IllegalStateException("hypothesis value semantics is missing");
            boolean locked = "USER_INPUT".equals(semantic.path("source").asText())
                && "LOCKED".equals(semantic.path("authority").asText());
            decisions.save(ConceptHypothesisDecision.initial(selection, type,
                mapper.writeValueAsString(value), semantic.path("source").asText(), locked, ownerId, now));
        }
    }

    private JsonNode alternative(Concept concept, ConceptHypothesisDecision current) {
        String attemptId = UUID.randomUUID().toString();
        JsonNode input = mapper.valueToTree(Map.of(
            "hypothesisType", current.getHypothesisType().name(),
            "rejectedValue", mapper.readTree(current.getProposedValueJson()),
            "proposalVersion", current.getProposalVersion() + 1,
            "candidate", mapper.readTree(concept.getCandidateJson())));
        JsonNode result = ai.execute(TaskType.CONCEPT_HYPOTHESIS_ALTERNATIVE,
            mapper.writeValueAsString(input), UUID.randomUUID().toString(), attemptId);
        if (!current.getHypothesisType().name().equals(result.path("hypothesisType").asText())
            || result.path("proposalVersion").asInt() != current.getProposalVersion() + 1) {
            throw new IllegalStateException("alternative proposal does not match request");
        }
        JsonNode proposed = result.path("proposedValue");
        validateValue(current.getHypothesisType(), proposed);
        if (canonical(proposed).equals(canonical(mapper.readTree(current.getProposedValueJson())))) {
            throw new IllegalStateException("alternative proposal must differ from rejected value");
        }
        return proposed;
    }

    private DeltaResult deltaReview(Concept concept, HypothesisType type, JsonNode finalValue, boolean userEdited) {
        ObjectNode changed = (ObjectNode) mapper.readTree(concept.getCandidateJson()).deepCopy();
        changed.set(type.candidateField(), finalValue);
        for (JsonNode semantic : changed.path("valueSemantics")) {
            if (type.candidateField().equals(semantic.path("fieldKey").asText()) && semantic.isObject()) {
                ObjectNode object = (ObjectNode) semantic;
                if (userEdited) object.put("source", "USER_INPUT");
                object.put("decision", userEdited ? "USER_EDITED_ACCEPTED" : "ACCEPTED");
            }
        }
        var pattern = legalFactPatterns.map(changed);
        var assessment = assessments.findByConceptIdAndProjectIdAndDeletedAtIsNull(concept.getId(), concept.getProjectId())
            .orElseThrow(() -> new IllegalStateException("selected concept legal assessment is missing"));
        var pack = assessment.getContextPack();
        JsonNode input = mapper.valueToTree(Map.of(
            "legalFactPattern", pattern.factPattern(),
            "factPatternHash", pattern.factPatternHash(),
            "externalFactContext", Map.of(
                "sourceSnapshotHash", pack.getSourceSnapshotHash(),
                "registryVersion", pack.getRegistryVersion(),
                "facts", mapper.readTree(pack.getCanonicalContextJson()))));
        String attemptId = UUID.randomUUID().toString();
        JsonNode result = ai.execute(TaskType.CONCEPT_LEGAL_REVIEW, mapper.writeValueAsString(input),
            UUID.randomUUID().toString(), attemptId);
        boolean passed = "IMPLEMENTABLE".equals(result.path("status").asText())
            || "IMPLEMENTABLE_WITH_CONTROLS".equals(result.path("status").asText());
        ObjectNode safe = result.isObject() ? (ObjectNode) result.deepCopy() : mapper.createObjectNode();
        if (safe.has("officialEvidence")) {
            var references = mapper.createArrayNode();
            for (JsonNode source : safe.path("officialEvidence")) {
                ObjectNode reference = references.addObject();
                for (String key : List.of("referenceIndex", "sourceType", "lawId", "officialIdentifier",
                        "lawName", "articleReference", "title", "officialSourceUri", "jurisdiction",
                        "promulgationDate", "effectiveDate", "retrievedAt", "contentHash", "registryVersion")) {
                    if (source.has(key) && !source.path(key).isNull()) reference.set(key, source.path(key).deepCopy());
                }
            }
            safe.set("officialEvidenceReferences", references);
        }
        safe.remove("officialEvidence");
        return new DeltaResult(passed, mapper.writeValueAsString(safe));
    }

    private void validateValue(HypothesisType type, JsonNode value) {
        boolean valid = value != null && !value.isMissingNode() && !value.isNull();
        if (type == HypothesisType.PRE_MARKET_SOM_SHARE) {
            valid = valid && value.isObject() && value.path("targetSharePercent").asDouble(0) > 0
                && value.path("horizonYears").asInt(0) > 0;
        } else if (type == HypothesisType.PRE_MARKET_SOM) {
            valid = valid && value.isObject() && value.path("amount").isNumber()
                && !value.path("currency").asText().isBlank();
        } else {
            valid = valid && value.isTextual() && !value.asText().isBlank();
        }
        if (!valid) throw new BusinessException(ErrorCode.HYPOTHESIS_VALUE_INVALID);
    }

    private String canonical(JsonNode value) {
        return new SnapshotHasher(mapper).hash(value);
    }

    private SelectionResponse response(ConceptSelection selection) {
        List<ConceptHypothesisDecision> latest = latest(selection.getId());
        return new SelectionResponse(selection.getId(), selection.getConceptId(), selection.getSelectionReason(),
            selection.getSelectedAt(), selection.isCurrentSelection(), latest.size() == HypothesisType.values().length
                && latest.stream().allMatch(ConceptHypothesisDecision::accepted),
            latest.stream().map(this::decisionResponse).toList());
    }

    private HypothesisDecisionResponse decisionResponse(ConceptHypothesisDecision value) {
        return new HypothesisDecisionResponse(value.getId(), value.getHypothesisType().name(),
            mapper.readTree(value.getProposedValueJson()), value.getSource(), value.getDecisionStatus().name(),
            value.getFinalValueJson() == null ? null : mapper.readTree(value.getFinalValueJson()),
            value.getProposalVersion(), value.isLocked(), value.getLegalImpact().name(),
            value.getLegalReviewStatus().name(), value.getDecidedAt());
    }

    private List<ConceptHypothesisDecision> latest(Long selectionId) {
        Map<HypothesisType, ConceptHypothesisDecision> latest = new EnumMap<>(HypothesisType.class);
        decisions.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(selectionId)
            .forEach(value -> latest.putIfAbsent(value.getHypothesisType(), value));
        List<ConceptHypothesisDecision> result = new ArrayList<>();
        for (HypothesisType type : HypothesisType.values()) if (latest.containsKey(type)) result.add(latest.get(type));
        return result;
    }

    private boolean allComplete(Long selectionId) {
        List<ConceptHypothesisDecision> latest = latest(selectionId);
        return latest.size() == HypothesisType.values().length && latest.stream().allMatch(ConceptHypothesisDecision::accepted);
    }

    private ConceptSelection currentSelection(Long projectId) {
        return selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "현재 컨셉 선택이 없습니다."));
    }

    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private record DeltaResult(boolean passed, String resultJson) {
        static DeltaResult notRequired() { return new DeltaResult(true, null); }
    }
}
