package com.aivle.backend.pipeline.selection.application;

import static com.aivle.backend.pipeline.selection.api.SelectionApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.application.ConceptLegalFactPatternMapper;
import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.repository.ConceptFactoryRunRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalAssessmentRepository;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver.Jurisdiction;
import com.aivle.backend.pipeline.selection.domain.*;
import com.aivle.backend.pipeline.selection.repository.ConceptHypothesisDecisionRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private final ObjectMapper mapper;
    private final LegalJurisdictionResolver jurisdictions;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher inputHasher;
    private final JobEventPublisher jobEvents;

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
            HypothesisActionRequest request, String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId);
        ConceptSelection selection = currentSelectionLocked(projectId);
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

        if (request.action() == HypothesisAction.REQUEST_ALTERNATIVE) {
            return queueAlternative(ownerId, projectId, selection, concept, current,
                idempotencyKey, correlationId);
        }
        JsonNode finalValue = request.action() == HypothesisAction.ACCEPT
            ? mapper.readTree(current.getProposedValueJson()) : request.value();
        validateValue(type, finalValue);
        boolean edited = request.action() == HypothesisAction.EDIT_AND_ACCEPT;
        JsonNode baseline = mapper.readTree(concept.getCandidateJson()).path(type.candidateField());
        boolean baselineChanged = !canonical(baseline).equals(canonical(finalValue));
        if (type.legalSensitive() && baselineChanged) {
            return queueDeltaLegal(ownerId, projectId, selection, concept, current,
                finalValue, baseline, edited, idempotencyKey, correlationId);
        }
        if (selection.hasActiveAction()) {
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING,
                "다른 가설 작업이 진행 중입니다.");
        }
        current.accept(mapper.writeValueAsString(finalValue), edited, ownerId, Instant.now(),
            baselineChanged, true, null);
        selection.completeSynchronousAction();
        ConceptHypothesisDecision result = decisions.save(current);
        return completedAction(result, allComplete(selection.getId()), request.action().name());
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
            boolean locked = ("USER_INPUT".equals(semantic.path("source").asText())
                || "USER_CONFIRMED".equals(semantic.path("source").asText()))
                && "LOCKED".equals(semantic.path("authority").asText());
            decisions.save(ConceptHypothesisDecision.initial(selection, type,
                mapper.writeValueAsString(value), semantic.path("source").asText(), locked, ownerId, now));
        }
    }

    private HypothesisActionResponse queueAlternative(Long ownerId, Long projectId, ConceptSelection selection,
            Concept concept, ConceptHypothesisDecision current, String idempotencyKey, String correlationId) {
        String key = commandKey(idempotencyKey);
        JsonNode input = mapper.valueToTree(Map.ofEntries(
            Map.entry("projectId", projectId), Map.entry("selectionId", selection.getId()),
            Map.entry("conceptId", concept.getId()),
            Map.entry("hypothesisType", current.getHypothesisType().name()),
            Map.entry("currentDecisionId", current.getId()),
            Map.entry("expectedProposalVersion", current.getProposalVersion()),
            Map.entry("rejectedValue", mapper.readTree(current.getProposedValueJson())),
            Map.entry("proposalVersion", current.getProposalVersion() + 1),
            Map.entry("candidate", mapper.readTree(concept.getCandidateJson())),
            Map.entry("candidateHash", concept.getCanonicalHash()),
            Map.entry("commandIdempotencyKey", key)
        ));
        return queueAction(ownerId, projectId, selection, current, TaskType.CONCEPT_HYPOTHESIS_ALTERNATIVE,
            HypothesisAction.REQUEST_ALTERNATIVE.name(), input, key, correlationId);
    }

    private HypothesisActionResponse queueDeltaLegal(Long ownerId, Long projectId, ConceptSelection selection,
            Concept concept, ConceptHypothesisDecision current, JsonNode finalValue, JsonNode baseline,
            boolean userEdited, String idempotencyKey, String correlationId) {
        HypothesisType type = current.getHypothesisType();
        String key = commandKey(idempotencyKey);
        if (type == HypothesisType.TARGET_REGION
                && jurisdictions.resolve(finalValue.asText()) != Jurisdiction.KR) {
            throw new BusinessException(ErrorCode.LEGAL_JURISDICTION_UNSUPPORTED);
        }
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
        JsonNode officialContext = mapper.readTree(pack.getCanonicalContextJson());
        JsonNode input = mapper.valueToTree(Map.ofEntries(
            Map.entry("projectId", projectId), Map.entry("selectionId", selection.getId()),
            Map.entry("conceptId", concept.getId()), Map.entry("candidateHash", concept.getCanonicalHash()),
            Map.entry("hypothesisType", type.name()), Map.entry("currentDecisionId", current.getId()),
            Map.entry("expectedProposalVersion", current.getProposalVersion()),
            Map.entry("requestedFinalValue", finalValue), Map.entry("baselineValue", baseline),
            Map.entry("userEdited", userEdited),
            Map.entry("legalFactPattern", pattern.factPattern()),
            Map.entry("legalFactPatternHash", pattern.factPatternHash()),
            Map.entry("factPatternHash", pattern.factPatternHash()),
            Map.entry("officialLegalContextReference", Map.of(
                "sourceSnapshotHash", pack.getSourceSnapshotHash(),
                "registryVersion", pack.getRegistryVersion(), "facts", officialContext)),
            Map.entry("externalFactContext", Map.of(
                "sourceSnapshotHash", pack.getSourceSnapshotHash(),
                "registryVersion", pack.getRegistryVersion(), "facts", officialContext)),
            Map.entry("commandIdempotencyKey", key)
        ));
        return queueAction(ownerId, projectId, selection, current, TaskType.CONCEPT_DELTA_LEGAL_REVIEW,
            userEdited ? HypothesisAction.EDIT_AND_ACCEPT.name() : HypothesisAction.ACCEPT.name(),
            input, key, correlationId);
    }

    private HypothesisActionResponse queueAction(Long ownerId, Long projectId, ConceptSelection selection,
            ConceptHypothesisDecision current, TaskType taskType, String actionType, JsonNode input,
            String idempotencyKey, String correlationId) {
        String inputJson = mapper.writeValueAsString(input);
        String key = commandKey(idempotencyKey);
        TaskRunService.CreateResult creation = taskRuns.createWithDisposition(ownerId, projectId, taskType,
            "CONCEPT_SELECTION", selection.getId().toString(), inputJson,
            inputHasher.hash(taskType, "1.0", "ko-KR", inputJson), key,
            correlationId == null || correlationId.isBlank() ? key : correlationId, 1);
        TaskRun run = creation.taskRun();
        if (creation.createdNew()) {
            selection.queueAction(run.getId(), actionType, current.getHypothesisType(),
                current.getId(), current.getProposalVersion());
            jobEvents.publish(new JobEventPublisher.Command(projectId, run.getId(), run.getId(), "QUEUED",
                taskType == TaskType.CONCEPT_HYPOTHESIS_ALTERNATIVE
                    ? "job.concept-selection.alternative.queued" : "job.concept-selection.delta-legal.queued",
                JobEvent.Status.QUEUED, "job.concept-selection.queued", Map.of(), null));
        }
        return new HypothesisActionResponse(decisionResponse(current), allComplete(selection.getId()),
            run.getId(), run.getId(), run.getState().name(), actionType,
            current.getHypothesisType().name(), current.getProposalVersion());
    }

    private HypothesisActionResponse completedAction(ConceptHypothesisDecision result,
            boolean complete, String actionType) {
        return new HypothesisActionResponse(decisionResponse(result), complete,
            null, null, "COMPLETED", actionType, result.getHypothesisType().name(), result.getProposalVersion());
    }

    private String commandKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return value.strip();
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
            latest.stream().map(this::decisionResponse).toList(), selection.getActiveActionTaskRunId(),
            selection.getPendingActionType(), selection.getPendingHypothesisType() == null ? null
                : selection.getPendingHypothesisType().name(), selection.getActionStatus(), selection.getSafeActionError());
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

    private ConceptSelection currentSelectionLocked(Long projectId) {
        ConceptSelection current = currentSelection(projectId);
        return selections.findByIdAndProjectIdAndDeletedAtIsNull(current.getId(), projectId)
            .filter(ConceptSelection::isCurrentSelection)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "현재 컨셉 선택이 없습니다."));
    }

    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

}
