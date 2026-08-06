package com.aivle.backend.pipeline.idea.application;

import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestion;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestionType;
import com.aivle.backend.pipeline.idea.repository.IdeaAnswerRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaQuestionRepository;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class IdeaBriefDerivationCommitService {
    private static final Set<String> RESULT_FIELDS = Set.of(
        "fields", "questions", "contradictions", "readiness", "userFacingSummary"
    );
    private static final Set<String> FIELD_FIELDS = Set.of(
        "fieldKey", "value", "decisionState", "provenance"
    );
    private static final Set<String> QUESTION_FIELDS = Set.of(
        "targetFieldKey", "prompt", "type", "options"
    );

    private final IdeaBriefRepository briefs;
    private final IdeaBriefFieldRepository fields;
    private final IdeaQuestionRepository questions;
    private final IdeaAnswerRepository answers;
    private final TaskRunService taskRuns;
    private final ObjectMapper mapper;

    @Transactional
    public CommitResult complete(
        TaskRunService.Claim claim,
        TaskRunWorkerContext context,
        ExecutionResponse response
    ) {
        JsonNode result = response.result();
        requireObject(result, RESULT_FIELDS);
        IdeaBrief brief = briefs.findByIdAndProjectIdAndDeletedAtIsNull(context.subjectId(), context.projectId())
            .orElseThrow(() -> new IllegalArgumentException("idea brief task subject not found"));
        if (!claim.taskRunId().equals(brief.getActiveTaskRunId())) {
            throw new IllegalStateException("idea brief active task changed");
        }

        for (JsonNode item : requireArray(result, "fields", 30)) {
            requireObject(item, FIELD_FIELDS);
            String fieldKey = text(item, "fieldKey", 80);
            String value = text(item, "value", 20_000);
            IdeaDecisionState decision = IdeaDecisionState.valueOf(text(item, "decisionState", 20));
            IdeaFieldProvenance provenance = IdeaFieldProvenance.valueOf(text(item, "provenance", 30));
            if (decision == IdeaDecisionState.LOCKED || provenance == IdeaFieldProvenance.USER_CONFIRMED) {
                throw new IllegalArgumentException("AI result violates field authority");
            }
            IdeaBriefField existing = fields.findByBriefIdAndFieldKey(brief.getId(), fieldKey).orElse(null);
            if (existing == null) fields.save(IdeaBriefField.aiProposal(brief, fieldKey, value, decision, provenance));
            else existing.applyAi(value, decision, provenance);
        }

        answers.deleteAllByBriefId(brief.getId());
        questions.deleteAllByBriefId(brief.getId());
        answers.flush();
        questions.flush();
        int order = 0;
        for (JsonNode item : requireArray(result, "questions", 4)) {
            requireObject(item, QUESTION_FIELDS);
            JsonNode options = item.get("options");
            if (options == null || !options.isArray() || options.size() > 12) {
                throw new IllegalArgumentException("question options invalid");
            }
            questions.save(IdeaQuestion.create(
                brief,
                text(item, "targetFieldKey", 80),
                IdeaQuestionType.valueOf(text(item, "type", 30)),
                text(item, "prompt", 500),
                mapper.writeValueAsString(options),
                order++
            ));
        }
        JsonNode readiness = result.get("readiness");
        if (readiness == null || !readiness.isObject()) throw new IllegalArgumentException("readiness invalid");
        String readinessStatus = text(readiness, "status", 30);
        if ("READY_FOR_REVIEW".equals(readinessStatus) && order == 0) brief.readyForReview();
        else brief.needsInput();

        taskRuns.adopt(
            claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(result), response.canonicalInputHash(), response.resultSchemaVersion()
        );
        return new CommitResult(brief.getStatus().name(), order);
    }

    @Transactional
    public void fail(String briefId, Long projectId) {
        briefs.findByIdAndProjectIdAndDeletedAtIsNull(briefId, projectId).ifPresent(IdeaBrief::failDerivation);
    }

    private JsonNode requireArray(JsonNode root, String name, int max) {
        JsonNode value = root.get(name);
        if (value == null || !value.isArray() || value.size() > max) {
            throw new IllegalArgumentException(name + " invalid");
        }
        return value;
    }

    private void requireObject(JsonNode value, Set<String> expected) {
        if (value == null || !value.isObject() || !Set.copyOf(value.propertyNames()).equals(expected)) {
            throw new IllegalArgumentException("result object fields invalid");
        }
    }

    private String text(JsonNode value, String name, int max) {
        JsonNode field = value.get(name);
        if (field == null || !field.isTextual() || field.asText().isBlank() || field.asText().length() > max) {
            throw new IllegalArgumentException(name + " invalid");
        }
        return field.asText();
    }

    public record CommitResult(String status, int questionCount) {}
}
