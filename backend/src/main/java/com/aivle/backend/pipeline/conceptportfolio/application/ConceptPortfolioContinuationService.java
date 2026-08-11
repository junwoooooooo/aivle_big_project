package com.aivle.backend.pipeline.conceptportfolio.application;

import static com.aivle.backend.pipeline.conceptportfolio.api.ConceptPortfolioApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.conceptportfolio.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.repository.*;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ConceptPortfolioContinuationService {
    private static final TaskType TYPE = TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE;
    private static final Set<String> TEXT_FACTS = Set.of(
        "sellerRole", "providerRole", "intermediaryRole");
    private static final Set<String> LIST_FACTS = Set.of(
        "transactionFlow", "paymentFlow", "partnerRequirements",
        "personalDataUsage", "physicalActivities");
    private final ConceptPortfolioRunRepository runs;
    private final ConceptPortfolioConceptRepository concepts;
    private final ConceptPortfolioContinuationRepository continuations;
    private final ConceptInputRequestRepository inputRequests;
    private final ConceptInputResponseRepository responses;
    private final UserRepository users;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher inputHasher;
    private final ConceptPortfolioJsonHasher jsonHasher;
    private final JobEventPublisher events;
    private final EffectiveAffectedFieldResolver affectedFields;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ConceptPortfolioContinuationService(ConceptPortfolioRunRepository runs,
            ConceptPortfolioConceptRepository concepts,
            ConceptPortfolioContinuationRepository continuations,
            ConceptInputRequestRepository inputRequests,
            ConceptInputResponseRepository responses, UserRepository users,
            TaskRunService taskRuns, CanonicalInputHasher inputHasher,
            ConceptPortfolioJsonHasher jsonHasher, JobEventPublisher events,
            EffectiveAffectedFieldResolver affectedFields, ObjectMapper mapper, Clock clock) {
        this.runs = runs; this.concepts = concepts; this.continuations = continuations;
        this.inputRequests = inputRequests; this.responses = responses; this.users = users;
        this.taskRuns = taskRuns; this.inputHasher = inputHasher; this.jsonHasher = jsonHasher;
        this.events = events; this.affectedFields = affectedFields;
        this.mapper = mapper; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InputRequestResponse> list(Long ownerId, Long projectId, String runId) {
        ConceptPortfolioRun run = owned(ownerId, projectId, runId);
        return inputRequests
            .findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(runId, projectId)
            .stream().map(value -> response(value, run)).toList();
    }

    @Transactional
    public ContinuationAcceptedResponse submit(Long ownerId, Long projectId, String runId,
            String inputRequestId, SubmitInputResponseRequest body) {
        ConceptPortfolioRun run = lockedOwned(ownerId, projectId, runId);
        ConceptInputRequest request = lockedRequest(run, projectId, inputRequestId);
        requireCurrent(run);
        ObjectNode responsePayload = responsePayload(body.confirmedFacts(), body.note());
        var replay = responses.findByInputRequestIdAndIdempotencyKeyAndDeletedAtIsNull(
            inputRequestId, body.idempotencyKey());
        if (replay.isPresent()) {
            if (!jsonHasher.hash(mapper.readTree(replay.get().getResponseJson()))
                    .equals(jsonHasher.hash(responsePayload))) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return accepted(replay.get(), request, run);
        }
        requireContinuable(run, request);
        validateFacts(body.confirmedFacts(), affectedFields.resolve(request));
        ConceptInputResponse saved = responses.save(ConceptInputResponse.create(request,
            users.findByIdAndDeletedAtIsNull(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)),
            mapper.writeValueAsString(responsePayload), body.idempotencyKey()));
        JsonNode taskInput = taskInput(run, request, body.confirmedFacts());
        TaskRun task = createTask(ownerId, projectId, run, body.idempotencyKey(), taskInput);
        request.answer(task.getId(), LocalDateTime.now(clock));
        run.attachContinuationTask(task.getId());
        publishQueued(projectId, task.getId());
        return accepted(saved, request, run);
    }

    @Transactional
    public ContinuationAcceptedResponse retry(Long ownerId, Long projectId, String runId,
            String inputRequestId, RetryContinuationRequest body) {
        ConceptPortfolioRun run = lockedOwned(ownerId, projectId, runId);
        ConceptInputRequest request = lockedRequest(run, projectId, inputRequestId);
        requireCurrent(run);
        if (!"CANDIDATE".equals(request.getScope()) || request.getContinuation() == null
                || request.getArtifactJson() == null) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        if (run.getActiveTaskRunId() != null) {
            TaskRun active = taskRuns.getOwned(ownerId, projectId, run.getActiveTaskRunId());
            if (body.idempotencyKey().equals(active.getIdempotencyKey())) {
                ConceptInputResponse stored = storedResponse(request);
                return accepted(stored, request, run);
            }
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }
        if (request.getStatus() != ConceptInputRequestStatus.ANSWERED
                || request.getContinuationTaskRunId() == null) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        TaskRun previous = taskRuns.getOwned(ownerId, projectId, request.getContinuationTaskRunId());
        if (previous.getState() != TaskRunState.FAILED
                && previous.getState() != TaskRunState.TIMED_OUT) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        ConceptInputResponse stored = storedResponse(request);
        JsonNode facts = mapper.readTree(stored.getResponseJson()).path("confirmedFacts");
        validateFacts(facts, affectedFields.resolve(request));
        JsonNode taskInput = taskInput(run, request, facts);
        TaskRun task = createTask(ownerId, projectId, run, body.idempotencyKey(), taskInput);
        request.attachRetry(task.getId());
        run.attachContinuationTask(task.getId());
        publishQueued(projectId, task.getId());
        return accepted(stored, request, run);
    }

    private TaskRun createTask(Long ownerId, Long projectId, ConceptPortfolioRun run,
            String idempotencyKey, JsonNode taskInput) {
        String inputJson = mapper.writeValueAsString(taskInput);
        String hash = inputHasher.hash(TYPE, "1.0", "ko-KR", inputJson);
        return taskRuns.create(ownerId, projectId, TYPE, "CONCEPT_PORTFOLIO_RUN", run.getId(),
            inputJson, hash, idempotencyKey, UUID.randomUUID().toString(), 2);
    }

    private JsonNode taskInput(ConceptPortfolioRun run, ConceptInputRequest request, JsonNode facts) {
        ConceptPortfolioContinuation context = continuations.findByRunIdAndDeletedAtIsNull(run.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID));
        if (request.getContinuation() == null || request.getArtifactJson() == null) {
            throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        }
        ObjectNode input = mapper.createObjectNode();
        input.put("contract", "concept-portfolio-v2-continuation-input-v1");
        input.put("contractVersion", "1.0");
        input.put("schemaVersion", "1.0");
        input.put("inputRequestId", request.getId());
        input.set("continuationContext", mapper.readTree(context.getContextJson()));
        ObjectNode artifact = (ObjectNode) mapper.readTree(request.getArtifactJson()).deepCopy();
        JsonNode effective = affectedFields.resolve(request);
        artifact.set("affectedFields", effective.deepCopy());
        JsonNode required = artifact.get("requiredInput");
        if (required != null && required.isObject()) {
            ((ObjectNode) required).set("affectedFields", effective.deepCopy());
        }
        input.set("continuationArtifact", artifact);
        input.set("confirmedFacts", facts.deepCopy());
        var comparison = input.putArray("comparisonConcepts");
        concepts.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderByDisplayOrder(
            run.getId(), run.getProject().getId()).stream().limit(5)
            .forEach(value -> comparison.add(mapper.readTree(value.getCandidateSnapshotJson())));
        return input;
    }

    private void validateFacts(JsonNode facts, JsonNode affected) {
        if (facts == null || !facts.isObject() || facts.isEmpty() || facts.size() > 8) {
            throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        }
        Set<String> affectedFields = new java.util.HashSet<>();
        if (affected != null && affected.isArray()) affected.forEach(item -> affectedFields.add(item.asText()));
        Set<String> providedFields = new java.util.HashSet<>();
        for (String name : facts.propertyNames()) {
            providedFields.add(name);
            JsonNode value = facts.get(name);
            if (!TEXT_FACTS.contains(name) && !LIST_FACTS.contains(name)) {
                throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
            }
            if (!affectedFields.isEmpty() && !affectedFields.contains(name)) {
                throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
            }
            if (TEXT_FACTS.contains(name)) {
                if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 4000)
                    throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
            } else {
                if (!value.isArray() || value.isEmpty() || value.size() > 20)
                    throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
                value.forEach(item -> {
                    if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 4000)
                        throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
                });
            }
        }
        if (!affectedFields.isEmpty() && !providedFields.equals(affectedFields)) {
            throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        }
    }

    private void requireContinuable(ConceptPortfolioRun run, ConceptInputRequest request) {
        if (run.getActiveTaskRunId() != null
                || request.getStatus() != ConceptInputRequestStatus.OPEN
                || !"CANDIDATE".equals(request.getScope())
                || request.getContinuation() == null || request.getArtifactJson() == null) {
            throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        }
    }

    private void requireCurrent(ConceptPortfolioRun run) {
        if (!run.isCurrent() || run.getProductStatus() == ConceptPortfolioRunStatus.STALE) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        }
    }

    private ConceptPortfolioRun owned(Long ownerId, Long projectId, String runId) {
        return runs.findOwned(ownerId, projectId, runId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ConceptPortfolioRun lockedOwned(Long ownerId, Long projectId, String runId) {
        owned(ownerId, projectId, runId);
        return runs.findLocked(runId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ConceptInputRequest lockedRequest(ConceptPortfolioRun run, Long projectId, String id) {
        ConceptInputRequest request = inputRequests.findLocked(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!request.getRun().getId().equals(run.getId())
                || !request.getProject().getId().equals(projectId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return request;
    }

    private ConceptInputResponse storedResponse(ConceptInputRequest request) {
        return responses.findFirstByInputRequestIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            request.getId()).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ObjectNode responsePayload(JsonNode facts, String note) {
        ObjectNode value = mapper.createObjectNode();
        value.set("confirmedFacts", facts == null ? mapper.nullNode() : facts.deepCopy());
        if (note == null || note.isBlank()) value.putNull("note"); else value.put("note", note.trim());
        return value;
    }

    private ContinuationAcceptedResponse accepted(ConceptInputResponse response,
            ConceptInputRequest request, ConceptPortfolioRun run) {
        return new ContinuationAcceptedResponse(response.getId(), request.getId(),
            request.getStatus().name(), request.getContinuationTaskRunId(), run.getId(),
            run.getActiveTaskRunId());
    }

    private InputRequestResponse response(ConceptInputRequest value, ConceptPortfolioRun run) {
        String question = first(value.getPresentationQuestionKo(), value.getSourceQuestion(),
            value.getSafeSummary());
        String nextAction;
        JsonNode affectedFields = this.affectedFields.resolve(value);
        if ("GLOBAL".equals(value.getScope())) nextAction = "UPDATE_IDEA_BRIEF";
        else if (value.getStatus() == ConceptInputRequestStatus.OPEN && affectedFields.isEmpty())
            nextAction = "INPUT_TARGET_UNRESOLVED";
        else if (value.getStatus() == ConceptInputRequestStatus.OPEN) nextAction = "PROVIDE_REQUIRED_INPUT";
        else if (value.getStatus() == ConceptInputRequestStatus.ANSWERED
                && value.getContinuationTaskRunId() != null
                && value.getContinuationTaskRunId().equals(run.getActiveTaskRunId())) nextAction = "WAIT";
        else if (value.getStatus() == ConceptInputRequestStatus.ANSWERED) nextAction = "RETRY_CONTINUATION";
        else nextAction = "NONE";
        JsonNode artifact = value.getArtifactJson() == null ? mapper.missingNode()
            : mapper.readTree(value.getArtifactJson());
        JsonNode candidate = artifact.path("candidateSnapshot").path("candidate");
        String displayName = first(text(candidate, "conceptName"), "추가 검토 중인 사업안");
        String oneLine = first(text(candidate, "conceptDefinition"), text(candidate, "introduction"),
            value.getSafeSummary());
        return new InputRequestResponse(value.getId(), value.getCandidateId(), value.getLineageId(),
            displayName, oneLine, value.getScope(), value.getStatus().name(), question,
            value.getReason(), value.getSafeSummary(),
            mapper.readTree(value.getUnknownFactsJson()), affectedFields,
            nextAction, value.getContinuationTaskRunId(), utc(value.getCreatedAt()),
            utc(value.getAnsweredAt()), utc(value.getResolvedAt()));
    }

    private String text(JsonNode value, String field) {
        JsonNode child = value.path(field);
        return child.isTextual() && !child.asText().isBlank() ? child.asText() : null;
    }

    private String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private java.time.Instant utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private void publishQueued(Long projectId, String taskId) {
        events.publish(new JobEventPublisher.Command(projectId, taskId, taskId,
            "QUEUED", "job.concept-portfolio.continuation.queued", JobEvent.Status.QUEUED,
            "job.concept-portfolio.continuation.queued", Map.of(), null));
    }
}
