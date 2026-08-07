package com.aivle.backend.pipeline.idea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.idea.application.IdeaBriefDerivationCommitService;
import com.aivle.backend.pipeline.idea.application.IdeaBriefReadinessCalculator;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaQuestionRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
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
        verify(taskRuns).adoptNeedsInput("task-1", "attempt-1", "claim-1", mapper.writeValueAsString(result),
            context.inputHash(), "1.0");
    }
}
