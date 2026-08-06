package com.aivle.backend.journey.conversation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.aivle.backend.document.parsing.DocumentParser;
import com.aivle.backend.file.storage.FileStorage;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.journey.brief.OpportunityBriefWorkspaceService;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import tools.jackson.databind.ObjectMapper;

class IdeaIntakeAiContractTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final IdeaIntakeAiService service = new IdeaIntakeAiService(
        mock(TaskRunService.class), mock(CanonicalInputHasher.class), mock(InternalAiExecutionClient.class),
        mock(ConversationService.class), mock(IdeaMessageRepository.class), mock(IdeaAttachmentRepository.class), mock(FileStorage.class),
        mock(DocumentParser.class), mock(OpportunityBriefWorkspaceService.class),
        mock(JobEventPublisher.class), mapper, mock(IdeaTurnCompletionService.class));

    @Test
    void acceptsStrictDraftAndQuestionContract() {
        assertThatCode(() -> service.validate(mapper.readTree(valid()))).doesNotThrowAnyException();
    }

    @Test
    void rejectsAutoConfirmedOrDefaultAssumptionSources() {
        String invalid = valid().replace("AI_PROPOSED", "USER_CONFIRMED");
        assertThatThrownBy(() -> service.validate(mapper.readTree(invalid)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void needsInputRequiresTwoToFourQuestions() {
        String invalid = valid().replace(", {\"id\":\"q2\",\"fieldKey\":\"targetRegion\",\"prompt\":\"어느 지역인가요?\",\"type\":\"FREE_TEXT\",\"options\":[],\"allowUndecided\":true}", "");
        assertThatThrownBy(() -> service.validate(mapper.readTree(invalid)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private String valid() {
        return """
            {"extractedFields":[],"fieldSuggestions":[{"fieldKey":"problem","valueJson":"waste","decisionStatus":"OPEN","sourceType":"AI_PROPOSED","confidence":0.5}],
             "assumptions":[],"openFields":["targetRegion"],"contradictions":[],
             "clarificationQuestions":[{"id":"q1","fieldKey":"targetCustomer","prompt":"누가 겪나요?","type":"FREE_TEXT","options":[],"allowUndecided":true}, {"id":"q2","fieldKey":"targetRegion","prompt":"어느 지역인가요?","type":"FREE_TEXT","options":[],"allowUndecided":true}],
             "readiness":"NEEDS_INPUT","userFacingSummary":"핵심 정보를 더 확인할게요."}
            """;
    }
}
