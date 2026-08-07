package com.aivle.backend.pipeline.idea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.idea.application.IdeaBriefDerivationCommitService;
import com.aivle.backend.pipeline.idea.application.IdeaBriefReadinessCalculator;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefFieldCatalog;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestion;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaQuestionRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IdeaBriefDerivationCommitServiceTests {
    @Test
    void persistsSafeAiAssessmentMetadataForLaterQueries() {
        IdeaBriefRepository briefs = mock(IdeaBriefRepository.class);
        IdeaBriefFieldRepository fields = mock(IdeaBriefFieldRepository.class);
        IdeaQuestionRepository questions = mock(IdeaQuestionRepository.class);
        TaskRunService taskRuns = mock(TaskRunService.class);
        ObjectMapper mapper = new ObjectMapper();
        IdeaBrief brief = IdeaBrief.initial(null, 7L);
        brief.updateOverview("overview");
        brief.startDeriving("task-1", "derive-key", "hash");
        when(briefs.findByIdAndProjectIdAndDeletedAtIsNull(brief.getId(), 42L)).thenReturn(Optional.of(brief));
        when(fields.findAllByBriefIdOrderById(brief.getId())).thenReturn(List.of());
        when(questions.findAllByBriefIdAndActiveTrueOrderByDisplayOrder(brief.getId())).thenReturn(List.of());
        IdeaBriefDerivationCommitService service = new IdeaBriefDerivationCommitService(
            briefs, fields, questions, taskRuns, mapper, new IdeaBriefReadinessCalculator(mapper));
        TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "claim-1");
        TaskRunWorkerContext context = new TaskRunWorkerContext(
            "task-1", 42L, 7L, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF", brief.getId(),
            "{}", "sha256:" + "a".repeat(64), "key", "correlation", "1.0", "1.0", "ko-KR", 1, 3);
        var result = mapper.readTree("""
            {"fields":[],"questions":[],
             "contradictions":[{"fieldKeys":["problem","targetCustomers"],"summary":"대상이 충돌합니다."}],
             "readiness":{"status":"NEEDS_INPUT","score":25,"missingFieldKeys":["problem"]},
             "userFacingSummary":"추가 확인이 필요합니다."}
            """);
        ExecutionResponse response = new ExecutionResponse(
            "1.0", "IDEA_BRIEF_DERIVATION", "1.0", "task-1", "attempt-1", "correlation",
            context.inputHash(), "1.0", result, mapper.createArrayNode(), mapper.createArrayNode(), null);

        service.complete(claim, context, response);

        assertThat(brief.getUserFacingSummary()).isEqualTo("추가 확인이 필요합니다.");
        assertThat(brief.getContradictionsJson()).contains("대상이 충돌합니다.");
        assertThat(brief.getMissingFieldKeysJson()).contains("problem");
        assertThat(brief.getReadinessScore()).isEqualTo(25);
        assertThat(brief.getAiReadinessStatus()).isEqualTo("NEEDS_INPUT");
        assertThat(brief.getStatus()).isEqualTo(IdeaBriefStatus.NEEDS_INPUT);
        verify(taskRuns).adoptNeedsInput("task-1", "attempt-1", "claim-1", mapper.writeValueAsString(result),
            context.inputHash(), "1.0");
    }

    @Test
    void finalSynthesisWithoutQuestionsMissingFieldsOrContradictionsIsReadyForReview() {
        TestContext test = completeContext(false);
        var result = test.mapper().readTree("""
            {"fields":[],"questions":[],"contradictions":[],
             "readiness":{"status":"NEEDS_INPUT","score":25,"missingFieldKeys":[]},
             "userFacingSummary":"review"}
            """);

        test.service().complete(test.claim(), test.worker(), response(test, result));

        assertThat(test.brief().getStatus()).isEqualTo(IdeaBriefStatus.READY_FOR_REVIEW);
        assertThat(new IdeaBriefReadinessCalculator(test.mapper())
            .calculate(test.brief(), test.fieldValues(), List.of(), true).readyForConfirm()).isTrue();
        verify(test.taskRuns()).adopt("task-1", "attempt-1", "claim-1",
            test.mapper().writeValueAsString(result), test.worker().inputHash(), "1.0");
    }

    @Test
    void finalSynthesisWithBlockingContradictionStillEntersReviewButCannotConfirm() {
        TestContext test = completeContext(true);
        var result = test.mapper().readTree("""
            {"fields":[],"questions":[],
             "contradictions":[{"fieldKeys":["problem","targetCustomers"],"summary":"conflict"}],
             "readiness":{"status":"NEEDS_INPUT","score":25,"missingFieldKeys":[]},
             "userFacingSummary":"review conflict"}
            """);

        test.service().complete(test.claim(), test.worker(), response(test, result));

        assertThat(test.brief().getStatus()).isEqualTo(IdeaBriefStatus.READY_FOR_REVIEW);
        assertThat(new IdeaBriefReadinessCalculator(test.mapper())
            .calculate(test.brief(), test.fieldValues(), List.of(), true).readyForConfirm()).isFalse();
    }

    @Test
    void unansweredQuestionKeepsTheBriefInNeedsInput() {
        TestContext test = completeContext(false);
        var result = test.mapper().readTree("""
            {"fields":[],
             "questions":[{"targetFieldKey":"problem","prompt":"more?","type":"FREE_TEXT","options":[]}],
             "contradictions":[],
             "readiness":{"status":"NEEDS_INPUT","score":70,"missingFieldKeys":[]},
             "userFacingSummary":"question"}
            """);
        TaskRunWorkerContext initialWorker = new TaskRunWorkerContext(
            "task-1", 42L, 7L, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF", test.brief().getId(),
            "{\"mode\":\"INITIAL\"}", test.worker().inputHash(), "key", "correlation",
            "1.0", "1.0", "ko-KR", 1, 3);

        test.service().complete(test.claim(), initialWorker, response(test, result));

        assertThat(test.brief().getStatus()).isEqualTo(IdeaBriefStatus.NEEDS_INPUT);
        verify(test.taskRuns()).adoptNeedsInput("task-1", "attempt-1", "claim-1",
            test.mapper().writeValueAsString(result), test.worker().inputHash(), "1.0");
    }

    private TestContext completeContext(boolean unresolvedContradiction) {
        IdeaBriefRepository briefs = mock(IdeaBriefRepository.class);
        IdeaBriefFieldRepository fields = mock(IdeaBriefFieldRepository.class);
        IdeaQuestionRepository questions = mock(IdeaQuestionRepository.class);
        TaskRunService taskRuns = mock(TaskRunService.class);
        ObjectMapper mapper = new ObjectMapper();
        IdeaBrief brief = IdeaBrief.initial(null, 7L);
        brief.updateOverview("overview");
        brief.startDeriving("task-1", "derive-key", "hash");
        List<IdeaBriefField> fieldValues = IdeaBriefFieldCatalog.fields().stream()
            .filter(IdeaBriefFieldCatalog.FieldDefinition::requiredForConcept)
            .map(definition -> unresolvedContradiction
                && (definition.key().equals("problem") || definition.key().equals("targetCustomers"))
                    ? IdeaBriefField.aiProposal(brief, definition.key(), "value",
                        IdeaDecisionState.PREFERRED, IdeaFieldProvenance.AI_PROPOSED)
                    : IdeaBriefField.userValue(brief, definition.key(), "value", IdeaDecisionState.PREFERRED))
            .toList();
        when(briefs.findByIdAndProjectIdAndDeletedAtIsNull(brief.getId(), 42L)).thenReturn(Optional.of(brief));
        when(fields.findAllByBriefIdOrderById(brief.getId())).thenReturn(fieldValues);
        List<IdeaQuestion> questionValues = new ArrayList<>();
        when(questions.findAllByBriefIdAndActiveTrueOrderByDisplayOrder(brief.getId()))
            .thenAnswer(invocation -> List.copyOf(questionValues));
        when(questions.save(any(IdeaQuestion.class))).thenAnswer(invocation -> {
            IdeaQuestion question = invocation.getArgument(0);
            questionValues.add(question);
            return question;
        });
        IdeaBriefDerivationCommitService service = new IdeaBriefDerivationCommitService(
            briefs, fields, questions, taskRuns, mapper, new IdeaBriefReadinessCalculator(mapper));
        TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "claim-1");
        TaskRunWorkerContext worker = new TaskRunWorkerContext(
            "task-1", 42L, 7L, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF", brief.getId(),
            "{\"mode\":\"FINAL_SYNTHESIS\"}", "sha256:" + "a".repeat(64), "key", "correlation",
            "1.0", "1.0", "ko-KR", 1, 3);
        return new TestContext(brief, fieldValues, taskRuns, mapper, service, claim, worker);
    }

    private ExecutionResponse response(TestContext test, tools.jackson.databind.JsonNode result) {
        return new ExecutionResponse(
            "1.0", "IDEA_BRIEF_DERIVATION", "1.0", "task-1", "attempt-1", "correlation",
            test.worker().inputHash(), "1.0", result, test.mapper().createArrayNode(),
            test.mapper().createArrayNode(), null);
    }

    private record TestContext(IdeaBrief brief, List<IdeaBriefField> fieldValues, TaskRunService taskRuns,
        ObjectMapper mapper, IdeaBriefDerivationCommitService service, TaskRunService.Claim claim,
        TaskRunWorkerContext worker) { }
}
