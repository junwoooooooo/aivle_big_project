package com.aivle.backend.pipeline.idea.application;

import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.idea.domain.IdeaAnswer;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
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
        String inputJson = objectMapper.writeValueAsString(request);
        String requestHash = sha256(inputJson);
        IdeaBrief brief = currentOrInitial(ownerId, projectId);
        if (replay(brief, "DERIVE", idempotencyKey, requestHash)) return response(brief);
        if (brief.isConfirmed()) brief = forkConfirmed(brief, ownerId);

        upsertUserFields(brief, request.fields());
        brief.replaceAttachments(request.attachmentFileIds() == null ? Set.of() : request.attachmentFileIds());
        briefs.save(brief);

        String inputHash = canonicalInputHasher.hash(TaskType.IDEA_BRIEF_DERIVATION, "1.0", "ko-KR", inputJson);
        TaskRun run = taskRuns.create(
            ownerId,
            projectId,
            TaskType.IDEA_BRIEF_DERIVATION,
            "IDEA_BRIEF",
            brief.getId(),
            inputJson,
            inputHash,
            idempotencyKey,
            correlationId == null || correlationId.isBlank() ? idempotencyKey : correlationId,
            3
        );
        brief.startDeriving(run.getId(), idempotencyKey, requestHash);
        jobEvents.publish(new JobEventPublisher.Command(
            projectId, run.getId(), run.getId(), "QUEUED", "job.idea.queued",
            JobEvent.Status.QUEUED, "job.idea.queued", Map.of(), null
        ));
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
        boolean keepReviewReady = brief.getStatus() == IdeaBriefStatus.READY_FOR_REVIEW;
        upsertUserFields(brief, request.fields());
        if (keepReviewReady) brief.readyForReview(); else brief.markDraft();
        brief.recordCommand("PATCH_FIELDS", idempotencyKey, requestHash);
        return response(brief);
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
        questions.findAllByBriefIdOrderByDisplayOrder(brief.getId())
            .forEach(question -> questionMap.put(question.getId(), question));
        for (AnswerCommand command : request.answers()) {
            IdeaQuestion question = questionMap.get(command.questionId());
            if (question == null) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idea Brief 질문을 찾을 수 없습니다.");
            if (answers.findByBriefIdAndQuestionIdAndIdempotencyKey(brief.getId(), question.getId(), idempotencyKey).isPresent()) continue;
            answers.save(IdeaAnswer.create(brief, question, command.answerJson(), idempotencyKey));
            question.markAnswered();
        }
        boolean complete = questionMap.values().stream().allMatch(IdeaQuestion::isAnswered);
        if (complete) brief.readyForReview(); else brief.needsInput();
        brief.recordCommand("ANSWERS", idempotencyKey, requestHash);
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
        if (questions.countByBriefIdAndAnsweredFalse(brief.getId()) > 0) {
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
        fields.saveAll(fields.findAllByBriefIdOrderById(confirmed.getId()).stream().map(field -> field.copyTo(draft)).toList());
        draft.replaceAttachments(confirmed.getAttachmentFileIds());
        return draft;
    }

    private void upsertUserFields(IdeaBrief brief, List<FieldCommand> commands) {
        if (commands == null) return;
        for (FieldCommand command : commands) {
            IdeaDecisionState decisionState = command.decisionState() == null ? IdeaDecisionState.OPEN : command.decisionState();
            IdeaBriefField field = fields.findByBriefIdAndFieldKey(brief.getId(), command.fieldKey()).orElse(null);
            if (field == null) fields.save(IdeaBriefField.userValue(brief, command.fieldKey(), command.value(), decisionState));
            else field.updateByUser(command.value(), decisionState);
        }
    }

    private IdeaBriefResponse response(IdeaBrief brief) {
        List<IdeaBriefField> fieldEntities = fields.findAllByBriefIdOrderById(brief.getId());
        List<IdeaQuestion> questionEntities = questions.findAllByBriefIdOrderByDisplayOrder(brief.getId());
        Map<String, String> answerByQuestion = new LinkedHashMap<>();
        answers.findAllByBriefIdOrderById(brief.getId())
            .forEach(answer -> answerByQuestion.put(answer.getQuestion().getId(), answer.getAnswerJson()));
        int missing = (int) fieldEntities.stream().filter(field ->
            field.getProvenance() == IdeaFieldProvenance.MISSING
                || field.getFieldValue() == null
                || field.getFieldValue().isBlank()
        ).count();
        int unanswered = (int) questionEntities.stream().filter(question -> !question.isAnswered()).count();
        return new IdeaBriefResponse(
            brief.getId(),
            brief.getStatus(),
            fieldEntities.stream().map(field -> new FieldView(
                field.getFieldKey(), field.getFieldValue(), field.getDecisionState(), field.getProvenance()
            )).toList(),
            questionEntities.stream().map(question -> new QuestionView(
                question.getId(), question.getTargetFieldKey(), question.getQuestionType(), question.getPrompt(),
                question.getOptionsJson(), question.isAnswered(), answerByQuestion.get(question.getId())
            )).toList(),
            new ReadinessView(
                fieldEntities.size(), missing, unanswered,
                brief.getStatus() == IdeaBriefStatus.READY_FOR_REVIEW && unanswered == 0
            ),
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
            "fields", fieldValues,
            "attachmentFileIds", brief.getAttachmentFileIds().stream().sorted().toList()
        )));
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
