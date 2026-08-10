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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class IdeaBriefDerivationCommitServiceTests {
    @Test
    void allowResultPersistsSafetyAndReviewableInterpretation() {
        TestContext test = context("FINAL_SYNTHESIS");
        JsonNode result = result(test.mapper(), "ALLOW", "[]");

        test.service().complete(test.claim(), test.worker(), response(test, result));

        assertThat(test.brief().getSafetyDecision()).isEqualTo("ALLOW");
        assertThat(test.brief().getInterpretationJson()).contains("interpretedProblem", "폐기 문제");
        assertThat(test.brief().getStatus()).isEqualTo(IdeaBriefStatus.READY_FOR_REVIEW);
        verify(test.taskRuns()).adopt("task-1", "attempt-1", "claim-1",
            test.mapper().writeValueAsString(result), test.worker().inputHash(), "1.0");
    }

    @Test
    void safetyBlockStopsThePipelineWithoutQuestions() {
        TestContext test = context("INITIAL");
        JsonNode result = result(test.mapper(), "BLOCK_OR_REFRAME", "[]");

        test.service().complete(test.claim(), test.worker(), response(test, result));

        assertThat(test.brief().getStatus()).isEqualTo(IdeaBriefStatus.SAFETY_BLOCKED);
        assertThat(test.brief().getActiveTaskRunId()).isNull();
    }

    @Test
    void coreAmbiguityQuestionKeepsSeedActionable() {
        TestContext test = context("INITIAL");
        JsonNode result = result(test.mapper(), "ALLOW", """
            [{"targetFieldKey":"problem","prompt":"어떤 문제인지 더 설명해 주세요.","type":"FREE_TEXT","options":[]}]
            """);

        test.service().complete(test.claim(), test.worker(), response(test, result));

        assertThat(test.brief().getStatus()).isEqualTo(IdeaBriefStatus.NEEDS_INPUT);
        verify(test.taskRuns()).adoptNeedsInput("task-1", "attempt-1", "claim-1",
            test.mapper().writeValueAsString(result), test.worker().inputHash(), "1.0");
    }

    private TestContext context(String mode) {
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
            .map(definition -> IdeaBriefField.userValue(
                brief, definition.key(), "value", IdeaDecisionState.LOCKED))
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
            "{\"mode\":\"" + mode + "\"}", "sha256:" + "a".repeat(64), "key", "correlation",
            "1.0", "1.0", "ko-KR", 1, 3);
        return new TestContext(brief, taskRuns, mapper, service, claim, worker);
    }

    private JsonNode result(ObjectMapper mapper, String safetyDecision, String questions) {
        return mapper.readTree("""
            {
              "safetyReview":{"decision":"%s","categories":[],"restrictions":[],"userFacingReason":"안전 확인 결과입니다."},
              "interpretation":{
                "interpretedProblem":"폐기 문제","interpretedTargetUsers":"지역 식당","usageContext":"영업 종료 후",
                "industryCategory":"폐기물 관리","researchScope":"감축 서비스","conciseIdeaDefinition":"폐기를 줄이는 서비스",
                "targetRegionInterpretation":"","relevantKnownCompetitorContext":""
              },
              "commitmentCandidates":[],
              "questions":%s,"contradictions":[],
              "readiness":{"status":"READY_FOR_REVIEW","score":90,"missingFieldKeys":[]},
              "userFacingSummary":"입력하신 아이디어를 이렇게 이해했습니다."
            }
            """.formatted(safetyDecision, questions));
    }

    private ExecutionResponse response(TestContext test, JsonNode result) {
        return new ExecutionResponse(
            "1.0", "IDEA_BRIEF_DERIVATION", "1.0", "task-1", "attempt-1", "correlation",
            test.worker().inputHash(), "1.0", result, test.mapper().createArrayNode(),
            test.mapper().createArrayNode(), null);
    }

    private record TestContext(IdeaBrief brief, TaskRunService taskRuns, ObjectMapper mapper,
        IdeaBriefDerivationCommitService service, TaskRunService.Claim claim,
        TaskRunWorkerContext worker) { }
}
