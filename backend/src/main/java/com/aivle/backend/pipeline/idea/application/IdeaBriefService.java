package com.aivle.backend.pipeline.idea.application;

import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.idea.domain.IdeaAnswer;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefFieldCatalog;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefDerivationMode;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestion;
import com.aivle.backend.pipeline.idea.repository.IdeaAnswerRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaQuestionRepository;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class IdeaBriefService {
    private final IdeaBriefRepository briefs;
    private final IdeaBriefFieldRepository fields;
    private final IdeaQuestionRepository questions;
    private final IdeaAnswerRepository answers;
    private final ProjectRepository projects;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher canonicalInputHasher;
    private final IdeaBriefIdempotencyPolicy idempotencyKeys;
    private final ObjectMapper objectMapper;
    private final JobEventPublisher jobEvents;
    private final IdeaBriefReadinessCalculator readinessCalculator;
    private final IdeaBriefAssessmentHasher assessmentHasher;

    @Transactional(readOnly = true)
    public IdeaBriefResponse get(Long ownerId, Long projectId) {
        return response(requireCurrent(ownerId, projectId));
    }

    @Transactional
    public IdeaBriefResponse derive(
        Long ownerId,
        Long projectId,
        DeriveRequest request,
        String rawIdempotencyKey,
        String correlationId
    ) {
        String idempotencyKey = idempotencyKeys.require(rawIdempotencyKey);
        List<FieldCommand> seedFields = seedCommands(request);
        String inputJson = objectMapper.writeValueAsString(Map.of(
            "mode", IdeaBriefDerivationMode.INITIAL.name(),
            "ideaOverview", request.ideaOverview(),
            "fields", seedFields,
            "attachmentFileIds", request.attachmentFileIds() == null ? Set.of() : request.attachmentFileIds(),
            "fieldMetadata", fieldMetadata()
        ));
        String requestHash = sha256(inputJson);
        IdeaBrief brief = currentOrInitial(ownerId, projectId);
        if (replay(brief, "DERIVE", idempotencyKey, requestHash)) return response(brief);
        if (brief.isConfirmed()) brief = forkConfirmed(brief, ownerId);

        brief.updateOverview(request.ideaOverview());
        upsertUserFields(brief, seedFields);
        brief.replaceAttachments(request.attachmentFileIds() == null ? Set.of() : request.attachmentFileIds());
        briefs.save(brief);

        String inputHash = canonicalInputHasher.hash(TaskType.IDEA_BRIEF_DERIVATION, "1.0", "ko-KR", inputJson);
        String executionKey = executionKey(brief, IdeaBriefDerivationMode.INITIAL, idempotencyKey);
        TaskRunService.CreateResult creation = taskRuns.createWithDisposition(
            ownerId,
            projectId,
            TaskType.IDEA_BRIEF_DERIVATION,
            "IDEA_BRIEF",
            brief.getId(),
            inputJson,
            inputHash,
            executionKey,
            correlationId == null || correlationId.isBlank() ? executionKey : correlationId,
            3
        );
        TaskRun run = requireReusableExecution(creation);
        brief.startDeriving(run.getId(), idempotencyKey, requestHash);
        if (creation.createdNew()) publishQueued(projectId, run);
        return response(brief);
    }

    @Transactional
    public IdeaBriefResponse patchFields(
        Long ownerId,
        Long projectId,
        PatchFieldsRequest request,
        String rawIdempotencyKey
    ) {
        String idempotencyKey = idempotencyKeys.require(rawIdempotencyKey);
        String requestHash = sha256(objectMapper.writeValueAsString(request));
        IdeaBrief brief = requireCurrentForUpdate(ownerId, projectId);
        if (replay(brief, "PATCH_FIELDS", idempotencyKey, requestHash)) return response(brief);
        if (brief.isConfirmed()) brief = forkConfirmed(brief, ownerId);
        boolean changed = upsertUserFields(brief, request.fields());
        if (changed) queueDerivation(ownerId, projectId, brief, idempotencyKey,
            IdeaBriefDerivationMode.FINAL_SYNTHESIS);
        brief.recordCommand("PATCH_FIELDS", idempotencyKey, requestHash);
        return response(brief);
    }

    @Transactional
    public IdeaBriefResponse patchInterpretation(
        Long ownerId,
        Long projectId,
        PatchInterpretationRequest request,
        String rawIdempotencyKey
    ) {
        String idempotencyKey = idempotencyKeys.require(rawIdempotencyKey);
        String requestHash = sha256(objectMapper.writeValueAsString(request));
        IdeaBrief brief = requireCurrentForUpdate(ownerId, projectId);
        if (replay(brief, "PATCH_INTERPRETATION", idempotencyKey, requestHash)) return response(brief);
        if (brief.isConfirmed()) brief = forkConfirmed(brief, ownerId);
        if (brief.getStatus() != IdeaBriefStatus.READY_FOR_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "아이디어 해석을 수정할 수 있는 상태가 아닙니다.");
        }
        JsonNode currentInterpretation = objectMapper.readTree(brief.getInterpretationJson());
        brief.updateInterpretation(objectMapper.writeValueAsString(Map.of(
            "interpretedProblem", request.interpretedProblem(),
            "interpretedTargetUsers", request.interpretedTargetUsers(),
            "usageContext", request.usageContext(),
            "industryCategory", request.industryCategory(),
            "researchScope", request.researchScope(),
            "conciseIdeaDefinition", request.conciseIdeaDefinition(),
            "targetRegionInterpretation", nullToEmpty(request.targetRegionInterpretation()),
            "relevantKnownCompetitorContext", nullToEmpty(request.relevantKnownCompetitorContext()),
            "commitmentCandidates", currentInterpretation.path("commitmentCandidates"),
            "userEdited", true
        )));
        brief.recordCommand("PATCH_INTERPRETATION", idempotencyKey, requestHash);
        return response(brief);
    }

    @Transactional
    public IdeaBriefResponse reviewCommitments(Long ownerId, Long projectId,
            ReviewCommitmentsRequest request, String rawIdempotencyKey) {
        String idempotencyKey = idempotencyKeys.require(rawIdempotencyKey);
        String requestHash = sha256(objectMapper.writeValueAsString(request));
        IdeaBrief brief = requireCurrentForUpdate(ownerId, projectId);
        if (replay(brief, "REVIEW_COMMITMENTS", idempotencyKey, requestHash)) return response(brief);
        if (brief.getStatus() != IdeaBriefStatus.READY_FOR_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "결정 후보를 검토할 수 있는 상태가 아닙니다.");
        }
        ObjectNode interpretation = (ObjectNode) objectMapper.readTree(brief.getInterpretationJson());
        Map<String, JsonNode> candidates = new LinkedHashMap<>();
        for (JsonNode candidate : interpretation.path("commitmentCandidates")) {
            candidates.put(candidate.path("fieldKey").asText(), candidate);
        }
        boolean canonicalChanged = false;
        for (CommitmentDecisionCommand command : request.commitments()) {
            IdeaBriefFieldCatalog.FieldDefinition definition = IdeaBriefFieldCatalog.require(command.fieldKey());
            if (definition.requiredForConcept() || !Set.of("CONFIRM", "EDIT_AND_CONFIRM", "RETURN_TO_OPEN").contains(command.action())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "결정 후보 Action이 올바르지 않습니다.");
            }
            IdeaBriefField field = fields.findByBriefIdAndFieldKey(brief.getId(), command.fieldKey()).orElse(null);
            if (field != null && field.getProvenance() == com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance.USER_INPUT
                    && field.getDecisionState() == IdeaDecisionState.LOCKED) {
                candidates.remove(command.fieldKey());
                continue;
            }
            if ("RETURN_TO_OPEN".equals(command.action())) {
                if (field != null) {
                    String before = canonicalFieldState(field);
                    field.returnCommitmentToOpen();
                    canonicalChanged |= !before.equals(canonicalFieldState(field));
                }
                candidates.remove(command.fieldKey());
                continue;
            }
            JsonNode candidate = candidates.get(command.fieldKey());
            String value = "EDIT_AND_CONFIRM".equals(command.action()) ? command.value()
                : candidate == null ? null : candidate.path("value").asText();
            if (value == null || value.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "확인할 결정값이 없습니다.");
            }
            if (field == null) {
                fields.save(IdeaBriefField.confirmedCommitment(brief, command.fieldKey(), value.trim()));
                canonicalChanged = true;
            } else {
                String before = canonicalFieldState(field);
                field.confirmCommitment(value.trim());
                canonicalChanged |= !before.equals(canonicalFieldState(field));
            }
            candidates.remove(command.fieldKey());
        }
        ArrayNode remaining = objectMapper.createArrayNode();
        candidates.values().forEach(value -> remaining.add(value.deepCopy()));
        interpretation.set("commitmentCandidates", remaining);
        brief.updateInterpretation(objectMapper.writeValueAsString(interpretation));
        if (canonicalChanged) {
            queueDerivation(ownerId, projectId, brief, idempotencyKey,
                IdeaBriefDerivationMode.FINAL_SYNTHESIS);
        }
        brief.recordCommand("REVIEW_COMMITMENTS", idempotencyKey, requestHash);
        return response(brief);
    }

    private String canonicalFieldState(IdeaBriefField field) {
        return (field.getFieldValue() == null ? "" : field.getFieldValue()) + "\u0000"
            + field.getDecisionState().name() + "\u0000" + field.getProvenance().name();
    }

    @Transactional
    public IdeaBriefResponse answer(
        Long ownerId,
        Long projectId,
        AnswersRequest request,
        String rawIdempotencyKey
    ) {
        String idempotencyKey = idempotencyKeys.require(rawIdempotencyKey);
        String requestHash = sha256(objectMapper.writeValueAsString(request));
        IdeaBrief brief = requireCurrentForUpdate(ownerId, projectId);
        if (replay(brief, "ANSWERS", idempotencyKey, requestHash)) return response(brief);
        brief.requireMutable();

        Map<String, IdeaQuestion> questionMap = new HashMap<>();
        questions.findAllByBriefIdAndActiveTrueOrderByDisplayOrder(brief.getId())
            .forEach(question -> questionMap.put(question.getId(), question));
        for (AnswerCommand command : request.answers()) {
            IdeaQuestion question = questionMap.get(command.questionId());
            if (question == null) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idea Brief 질문을 찾을 수 없습니다.");
            if (answers.findByBriefIdAndQuestionIdAndIdempotencyKey(brief.getId(), question.getId(), idempotencyKey).isPresent()) continue;
            if (question.isAnswered()) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idea Brief question is already answered.");
            answers.save(IdeaAnswer.create(brief, question, command.answerJson(), idempotencyKey));
            applyAnswerToField(brief, question, command.answerJson());
            question.markAnswered();
        }
        boolean complete = questionMap.values().stream().allMatch(IdeaQuestion::isAnswered);
        if (!complete) {
            int unanswered = (int) questionMap.values().stream().filter(question -> !question.isAnswered()).count();
            brief.needsInput(unanswered, 0);
        } else if (brief.getClarificationRound() < IdeaBriefReadinessCalculator.MAX_CLARIFICATION_ROUNDS) {
            queueDerivation(ownerId, projectId, brief, idempotencyKey, IdeaBriefDerivationMode.CLARIFICATION);
        } else {
            queueDerivation(ownerId, projectId, brief, idempotencyKey, IdeaBriefDerivationMode.FINAL_SYNTHESIS);
        }
        brief.recordCommand("ANSWERS", idempotencyKey, requestHash);
        return response(brief);
    }

    @Transactional
    public IdeaBriefResponse reanalyze(
        Long ownerId,
        Long projectId,
        String rawIdempotencyKey
    ) {
        String idempotencyKey = idempotencyKeys.require(rawIdempotencyKey);
        IdeaBrief brief = requireCurrentForUpdate(ownerId, projectId);
        String requestHash = sha256("FINAL_SYNTHESIS:" + assessmentHasher.hash(
            brief, fields.findAllByBriefIdOrderById(brief.getId())));
        if (replay(brief, "REANALYZE", idempotencyKey, requestHash)) return response(brief);
        brief.requireMutable();
        if (brief.getStatus() == IdeaBriefStatus.DERIVING) {
            TaskRun active = activeTaskRun(brief);
            if (active != null && activeExecutionState(active.getState())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "ANALYSIS_ALREADY_RUNNING");
            }
        }
        queueDerivation(ownerId, projectId, brief, idempotencyKey, IdeaBriefDerivationMode.FINAL_SYNTHESIS);
        brief.recordCommand("REANALYZE", idempotencyKey, requestHash);
        return response(brief);
    }

    @Transactional
    public IdeaBriefResponse confirm(
        Long ownerId,
        Long projectId,
        ConfirmRequest request,
        String rawIdempotencyKey
    ) {
        String idempotencyKey = idempotencyKeys.require(rawIdempotencyKey);
        String requestHash = sha256(objectMapper.writeValueAsString(request));
        IdeaBrief brief = requireCurrentForUpdate(ownerId, projectId);
        if (replay(brief, "CONFIRM", idempotencyKey, requestHash)) return response(brief);
        if (request.expectedVersion() != null && !request.expectedVersion().equals(brief.getVersion())) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        List<IdeaBriefField> currentFields = fields.findAllByBriefIdOrderById(brief.getId());
        if (brief.getStatus() != IdeaBriefStatus.READY_FOR_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idea Brief가 검토 준비 상태가 아닙니다.");
        }
        boolean assessmentCurrent = assessmentHasher.hash(brief, currentFields)
            .equals(brief.getAssessmentInputHash());
        if (!readinessCalculator.calculate(brief, currentFields,
                questions.findAllByBriefIdAndActiveTrueOrderByDisplayOrder(brief.getId()), assessmentCurrent)
            .readyForConfirm()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idea Brief readiness requirements are not satisfied.");
        }
        if (questions.countByBriefIdAndActiveTrueAndAnsweredFalse(brief.getId()) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "답변하지 않은 Idea Brief 질문이 있습니다.");
        }
        brief.confirm(snapshotHash(brief, currentFields), idempotencyKey, requestHash);
        return response(brief);
    }

    private IdeaBrief currentOrInitial(Long ownerId, Long projectId) {
        IdeaBrief current = briefs.findCurrentOwnedForUpdate(ownerId, projectId).orElse(null);
        if (current != null) return current;
        Project project = projects.findByIdForUpdate(projectId)
            .filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        return briefs.save(IdeaBrief.initial(project, ownerId));
    }

    private IdeaBrief requireCurrent(Long ownerId, Long projectId) {
        return briefs.findCurrentOwned(ownerId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Idea Brief를 찾을 수 없습니다."));
    }

    private IdeaBrief requireCurrentForUpdate(Long ownerId, Long projectId) {
        return briefs.findCurrentOwnedForUpdate(ownerId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Idea Brief를 찾을 수 없습니다."));
    }

    private IdeaBrief forkConfirmed(IdeaBrief confirmed, Long ownerId) {
        IdeaBrief draft = briefs.save(IdeaBrief.nextDraft(confirmed, ownerId));
        draft.copyCanonicalStateFrom(confirmed);
        fields.saveAll(fields.findAllByBriefIdOrderById(confirmed.getId()).stream().map(field -> field.copyTo(draft)).toList());
        draft.replaceAttachments(confirmed.getAttachmentFileIds());
        return draft;
    }

    private boolean upsertUserFields(IdeaBrief brief, List<FieldCommand> commands) {
        if (commands == null) return false;
        boolean changed = false;
        for (FieldCommand command : commands) {
            IdeaBriefFieldCatalog.FieldDefinition definition = IdeaBriefFieldCatalog.require(command.fieldKey());
            IdeaBriefField field = fields.findByBriefIdAndFieldKey(brief.getId(), command.fieldKey()).orElse(null);
            String value = command.value() == null ? "" : command.value().trim();
            if (value.isBlank() && !definition.requiredForConcept() && field == null) continue;
            IdeaDecisionState decisionState = value.isBlank() ? IdeaDecisionState.OPEN : IdeaDecisionState.LOCKED;
            if (field == null) {
                fields.save(IdeaBriefField.userValue(brief, command.fieldKey(), value, decisionState));
                changed = true;
            } else if (!java.util.Objects.equals(field.getFieldValue(), value)
                    || field.getDecisionState() != decisionState
                    || (field.getProvenance() != com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance.USER_INPUT
                        && field.getProvenance() != com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance.USER_CONFIRMED)) {
                if (value.isBlank()) field.updateFromAnswer("", IdeaDecisionState.OPEN, true);
                else field.updateByUser(value, decisionState);
                changed = true;
            }
        }
        return changed;
    }

    private IdeaBriefResponse response(IdeaBrief brief) {
        List<IdeaBriefField> fieldEntities = fields.findAllByBriefIdOrderById(brief.getId());
        List<IdeaQuestion> questionEntities = questions.findAllByBriefIdAndActiveTrueOrderByDisplayOrder(brief.getId());
        Map<String, String> answerByQuestion = new LinkedHashMap<>();
        answers.findAllByBriefIdOrderById(brief.getId())
            .forEach(answer -> answerByQuestion.put(answer.getQuestion().getId(), answer.getAnswerJson()));
        boolean assessmentCurrent = brief.getAssessmentInputHash() != null
            && brief.getAssessmentInputHash().equals(assessmentHasher.hash(brief, fieldEntities));
        IdeaBriefReadinessCalculator.Assessment assessment = readinessCalculator.calculate(
            brief, fieldEntities, questionEntities, assessmentCurrent);
        ExecutionConsistency execution = executionConsistency(brief);
        return new IdeaBriefResponse(
            brief.getId(),
            brief.getStatus(),
            brief.getOverviewText(),
            fieldEntities.stream().map(field -> new FieldView(
                field.getFieldKey(), IdeaBriefReadinessCalculator.UNDECIDED.equals(field.getFieldValue())
                    ? "" : field.getFieldValue(), field.getDecisionState(), field.getProvenance(),
                IdeaBriefReadinessCalculator.UNDECIDED.equals(field.getFieldValue())
            )).toList(),
            questionEntities.stream().map(question -> new QuestionView(
                question.getId(), question.getTargetFieldKey(), question.getQuestionType(), question.getPrompt(),
                question.getOptionsJson(), question.isAnswered(), answerByQuestion.get(question.getId())
            )).toList(),
            IdeaBriefFieldCatalog.fields().stream().map(definition -> new FieldCatalogView(
                definition.key(), definition.label(), definition.requiredForConcept(),
                definition.defaultDecisionState(), definition.regulatorySensitive(),
                definition.allowedQuestionTypes()
            )).toList(),
            safetyReview(brief),
            interpretation(brief),
            brief.getUserFacingSummary(),
            readinessCalculator.contradictions(brief.getContradictionsJson()).stream()
                .map(value -> new ContradictionView(value.fieldKeys(), value.summary())).toList(),
            new ReadinessView(
                assessment.totalRequiredFieldCount(), assessment.completedRequiredFieldCount(),
                assessment.missingFieldKeys(), assessment.unansweredQuestionCount(),
                assessment.contradictionCount(), assessment.score(), assessment.readyForConfirm()
            ),
            brief.getClarificationRound(),
            IdeaBriefReadinessCalculator.MAX_CLARIFICATION_ROUNDS,
            assessmentCurrent,
            execution.consistent(),
            execution.recoveryRequired(),
            brief.getActiveTaskRunId(),
            brief.getConfirmedSnapshotId(),
            brief.getUpdatedAt()
        );
    }

    private String snapshotHash(IdeaBrief brief, List<IdeaBriefField> currentFields) {
        List<Map<String, Object>> fieldValues = currentFields.stream()
            .sorted(Comparator.comparing(IdeaBriefField::getFieldKey))
            .map(field -> Map.<String, Object>of(
                "fieldKey", field.getFieldKey(),
                "value", field.getFieldValue() == null ? "" : field.getFieldValue(),
                "decisionState", field.getDecisionState().name(),
                "provenance", field.getProvenance().name()
            )).toList();
        return sha256(objectMapper.writeValueAsString(Map.of(
            "briefId", brief.getId(),
            "sequence", brief.getBriefSequence(),
            "overview", brief.getOverviewText() == null ? "" : brief.getOverviewText(),
            "fields", fieldValues,
            "safetyDecision", brief.getSafetyDecision() == null ? "" : brief.getSafetyDecision(),
            "interpretation", brief.getInterpretationJson(),
            "userFacingSummary", brief.getUserFacingSummary() == null ? "" : brief.getUserFacingSummary(),
            "contradictions", readinessCalculator.contradictions(brief.getContradictionsJson()),
            "attachmentFileIds", brief.getAttachmentFileIds().stream().sorted().toList()
        )));
    }

    private void applyAnswerToField(IdeaBrief brief, IdeaQuestion question, String answerJson) {
        IdeaBriefFieldCatalog.FieldDefinition definition = IdeaBriefFieldCatalog.require(question.getTargetFieldKey());
        if (!definition.allowedQuestionTypes().contains(question.getQuestionType())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question type is not allowed for the target field.");
        }
        String normalized = normalizeAnswer(answerJson);
        boolean undecided = IdeaBriefReadinessCalculator.UNDECIDED.equals(normalized);
        IdeaBriefField field = fields.findByBriefIdAndFieldKey(brief.getId(), definition.key()).orElse(null);
        if (field == null) {
            fields.save(IdeaBriefField.userAnswer(
                brief, definition.key(), normalized, definition.defaultDecisionState(), undecided));
        } else field.updateFromAnswer(normalized, definition.defaultDecisionState(), undecided);
    }

    private String normalizeAnswer(String answerJson) {
        JsonNode value;
        try {
            value = objectMapper.readTree(answerJson);
        } catch (RuntimeException invalid) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idea Brief answer is invalid.");
        }
        if (value.isTextual()) {
            String text = value.asText().trim();
            if (text.isBlank()) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idea Brief answer is empty.");
            return IdeaBriefReadinessCalculator.UNDECIDED.equals(text) || "**UNDECIDED**".equals(text)
                ? IdeaBriefReadinessCalculator.UNDECIDED : text;
        }
        if (value.isArray()) {
            List<String> values = java.util.stream.StreamSupport.stream(value.spliterator(), false)
                .filter(JsonNode::isTextual).map(JsonNode::asText).map(String::trim)
                .filter(text -> !text.isBlank()).distinct().sorted().toList();
            if (values.isEmpty()) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idea Brief answer is empty.");
            return objectMapper.writeValueAsString(values);
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idea Brief answer type is invalid.");
    }

    private IdeaBriefReadinessCalculator.Assessment readiness(IdeaBrief brief) {
        return readinessCalculator.calculate(brief,
            fields.findAllByBriefIdOrderById(brief.getId()),
            questions.findAllByBriefIdAndActiveTrueOrderByDisplayOrder(brief.getId()));
    }

    private void queueDerivation(Long ownerId, Long projectId, IdeaBrief brief, String rawCommandIdempotencyKey,
            IdeaBriefDerivationMode mode) {
        String inputJson = objectMapper.writeValueAsString(Map.of(
            "mode", mode.name(),
            "ideaOverview", brief.getOverviewText(),
            "fields", fields.findAllByBriefIdOrderById(brief.getId()).stream().map(field -> Map.of(
                "fieldKey", field.getFieldKey(),
                "value", IdeaBriefReadinessCalculator.UNDECIDED.equals(field.getFieldValue()) ? "" : field.getFieldValue(),
                "decisionState", field.getDecisionState().name()
            )).toList(),
            "attachmentFileIds", brief.getAttachmentFileIds().stream().sorted().toList(),
            "fieldMetadata", fieldMetadata()
        ));
        String inputHash = canonicalInputHasher.hash(TaskType.IDEA_BRIEF_DERIVATION, "1.0", "ko-KR", inputJson);
        String key = executionKey(brief, mode, rawCommandIdempotencyKey);
        TaskRunService.CreateResult creation = taskRuns.createWithDisposition(
            ownerId, projectId, TaskType.IDEA_BRIEF_DERIVATION,
            "IDEA_BRIEF", brief.getId(), inputJson, inputHash, key, key, 3);
        TaskRun run = requireReusableExecution(creation);
        if (mode == IdeaBriefDerivationMode.CLARIFICATION) brief.startClarification(run.getId());
        else brief.startFinalSynthesis(run.getId());
        if (creation.createdNew()) publishQueued(projectId, run);
    }

    private TaskRun requireReusableExecution(TaskRunService.CreateResult creation) {
        TaskRun run = creation.taskRun();
        if (creation.replayed() && run.terminal()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                "Terminal task execution cannot be reused.");
        }
        return run;
    }

    private void publishQueued(Long projectId, TaskRun run) {
        jobEvents.publish(new JobEventPublisher.Command(projectId, run.getId(), run.getId(),
            "QUEUED", "job.idea.queued", JobEvent.Status.QUEUED, "job.idea.queued", Map.of(), null));
    }

    private String executionKey(IdeaBrief brief, IdeaBriefDerivationMode mode, String rawCommandKey) {
        return sha256("IDEA_BRIEF_DERIVATION:" + brief.getId() + ":" + mode.name() + ":" + rawCommandKey);
    }

    private ExecutionConsistency executionConsistency(IdeaBrief brief) {
        if (brief.getStatus() != IdeaBriefStatus.DERIVING) return new ExecutionConsistency(true, false);
        TaskRun active = activeTaskRun(brief);
        boolean consistent = active != null && activeExecutionState(active.getState());
        return new ExecutionConsistency(consistent, !consistent);
    }

    private TaskRun activeTaskRun(IdeaBrief brief) {
        if (brief.getActiveTaskRunId() == null) return null;
        try {
            return taskRuns.getOwnedForWorker(brief.getActiveTaskRunId());
        } catch (RuntimeException missing) {
            return null;
        }
    }

    private boolean activeExecutionState(TaskRunState state) {
        return state == TaskRunState.QUEUED || state == TaskRunState.READY || state == TaskRunState.RUNNING;
    }

    private record ExecutionConsistency(boolean consistent, boolean recoveryRequired) { }

    private List<Map<String, Object>> fieldMetadata() {
        return IdeaBriefFieldCatalog.fields().stream().map(definition -> Map.<String, Object>of(
            "fieldKey", definition.key(),
            "requiredForConcept", definition.requiredForConcept(),
            "regulatorySensitive", definition.regulatorySensitive()
        )).toList();
    }

    private List<FieldCommand> seedCommands(DeriveRequest request) {
        java.util.ArrayList<FieldCommand> values = new java.util.ArrayList<>();
        values.add(new FieldCommand("ideaOverview", request.ideaOverview(), IdeaDecisionState.LOCKED));
        values.add(new FieldCommand("problem", request.problem(), IdeaDecisionState.LOCKED));
        values.add(new FieldCommand("targetUsers", request.targetUsers(), IdeaDecisionState.LOCKED));
        OptionalSeedRequest optional = request.optionalSeed();
        if (optional == null) return List.copyOf(values);
        addOptional(values, "targetRegion", optional.targetRegion());
        addOptional(values, "knownCompetitors", optional.knownCompetitors());
        addOptional(values, "revenueModel", optional.revenueModel());
        addOptional(values, "price", optional.price());
        addOptional(values, "channels", optional.channels());
        addOptional(values, "differentiators", optional.differentiators());
        if (optional.constraints() != null) {
            addOptional(values, "budgetConstraint", optional.constraints().budgetConstraint());
            addOptional(values, "teamConstraint", optional.constraints().teamConstraint());
            addOptional(values, "timelineConstraint", optional.constraints().timelineConstraint());
            addOptional(values, "otherConstraint", optional.constraints().otherConstraint());
        }
        return List.copyOf(values);
    }

    private void addOptional(List<FieldCommand> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.add(new FieldCommand(key, value.trim(), IdeaDecisionState.LOCKED));
        }
    }

    private SafetyReviewView safetyReview(IdeaBrief brief) {
        if (brief.getSafetyDecision() == null) return null;
        return new SafetyReviewView(
            brief.getSafetyDecision(), textArray(brief.getSafetyCategoriesJson()),
            textArray(brief.getSafetyRestrictionsJson()), brief.getSafetyUserFacingReason());
    }

    private IdeaInterpretationView interpretation(IdeaBrief brief) {
        try {
            JsonNode value = objectMapper.readTree(brief.getInterpretationJson());
            if (value == null || !value.isObject() || value.isEmpty()) return null;
            return new IdeaInterpretationView(
                value.path("interpretedProblem").asText(""),
                value.path("interpretedTargetUsers").asText(""),
                value.path("usageContext").asText(""),
                value.path("industryCategory").asText(""),
                value.path("researchScope").asText(""),
                value.path("conciseIdeaDefinition").asText(""),
                value.path("targetRegionInterpretation").asText(""),
                value.path("relevantKnownCompetitorContext").asText(""),
                commitmentCandidates(value),
                "AI_DERIVED", "REVIEWABLE", value.path("userEdited").asBoolean(false),
                brief.getInterpretationConfirmedAt() != null
            );
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private List<CommitmentCandidateView> commitmentCandidates(JsonNode interpretation) {
        JsonNode values = interpretation.path("commitmentCandidates");
        if (!values.isArray()) return List.of();
        java.util.ArrayList<CommitmentCandidateView> result = new java.util.ArrayList<>();
        for (JsonNode value : values) result.add(new CommitmentCandidateView(
            value.path("fieldKey").asText(), value.path("value").asText(),
            value.path("evidenceQuote").asText(), value.path("source").asText("AI_DERIVED"),
            value.path("origin").asText("USER_TEXT"), value.path("authority").asText("REVIEWABLE")
        ));
        return List.copyOf(result);
    }

    private List<String> textArray(String json) {
        try {
            JsonNode values = objectMapper.readTree(json == null ? "[]" : json);
            if (!values.isArray()) return List.of();
            return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .filter(JsonNode::isTextual).map(JsonNode::asText).toList();
        } catch (RuntimeException invalid) {
            return List.of();
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean replay(IdeaBrief brief, String command, String idempotencyKey, String requestHash) {
        try {
            return brief.replay(command, idempotencyKey, requestHash);
        } catch (IllegalStateException conflict) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
