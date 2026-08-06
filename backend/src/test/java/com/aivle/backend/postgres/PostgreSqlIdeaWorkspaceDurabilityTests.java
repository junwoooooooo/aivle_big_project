package com.aivle.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.journey.brief.FieldDecisionStatus;
import com.aivle.backend.journey.brief.FieldSourceType;
import com.aivle.backend.journey.brief.OpportunityBriefVersionRepository;
import com.aivle.backend.journey.brief.OpportunityBriefWorkspaceService;
import com.aivle.backend.journey.conversation.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
class PostgreSqlIdeaWorkspaceDurabilityTests extends PostgreSqlIntegrationTestSupport {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ConversationService conversations;
    @Autowired IdeaMessageRepository messages;
    @Autowired OpportunityBriefWorkspaceService briefs;
    @Autowired OpportunityBriefVersionRepository versions;
    @Autowired TaskRunService tasks;
    @Autowired CanonicalInputHasher hasher;
    @Autowired ObjectMapper mapper;

    @Test
    void storesStrictEnvelopeAndStructuralProvenance() {
        Context context = context();
        IdeaMessage source = conversations.appendMessage(context.owner.getId(), context.project.getId(),
            context.conversation.getId(), IdeaMessage.Role.USER, "source");
        TaskRun run = task(context, TaskType.IDEA_CONVERSATION_TURN, "message", source.getId().toString());
        var envelope = IdeaMessageContract.assistant(mapper, IdeaMessageContract.Type.QUESTION_SET,
            "질문", List.of(new IdeaMessageContract.Question("q1", "problem", "문제는?",
                IdeaMessageContract.QuestionType.FREE_TEXT, List.of(), true)), List.of(), "NEEDS_INPUT");
        IdeaMessage assistant = conversations.appendAssistant(context.owner.getId(), context.project.getId(),
            context.conversation.getId(), envelope, run);
        var brief = briefs.mergeAiDraft(context.owner.getId(), context.project.getId(), context.conversation.getId(),
            List.of(new OpportunityBriefWorkspaceService.AiField("problem", mapper.valueToTree("waste"),
                FieldDecisionStatus.OPEN, FieldSourceType.AI_PROPOSED, .7)), source.getId(), null, run);

        assertThat(IdeaMessageContract.view(mapper, assistant).envelope().schemaVersion()).isEqualTo("1.0");
        assertThat(brief.fields()).singleElement().satisfies(field -> {
            assertThat(field.sourceMessageId()).isEqualTo(source.getId());
            assertThat(field.confidence()).isEqualTo(.7);
            assertThat(field.userConfirmed()).isFalse();
        });
    }

    @Test
    void taskRunLinksPreventDuplicateAssistantAndBriefVersion() {
        Context context = context();
        IdeaMessage source = conversations.appendMessage(context.owner.getId(), context.project.getId(),
            context.conversation.getId(), IdeaMessage.Role.USER, "source");
        TaskRun run = task(context, TaskType.IDEA_CONVERSATION_TURN, "message", source.getId().toString());
        var envelope = IdeaMessageContract.assistant(mapper, IdeaMessageContract.Type.TEXT,
            "done", List.of(), List.of(), null);
        conversations.appendAssistant(context.owner.getId(), context.project.getId(), context.conversation.getId(), envelope, run);
        assertThatThrownBy(() -> {
            conversations.appendAssistant(context.owner.getId(), context.project.getId(), context.conversation.getId(), envelope, run);
            messages.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expiredLeaseIsRecoveredAndCanBeClaimedByAnotherWorker() {
        Context context = context();
        TaskRun run = task(context, TaskType.IDEA_ATTACHMENT_PARSE, "attachment", "1");
        TaskRunService.Claim first = tasks.claim(run.getId(), "worker-a", Duration.ZERO, Duration.ofMinutes(2));
        tasks.startExecution(run.getId(), first.taskAttemptId(), first.claimToken());
        assertThat(tasks.recoverExpired(Duration.ZERO, List.of(TaskType.IDEA_ATTACHMENT_PARSE))).isEqualTo(1);
        TaskRunService.Claim second = tasks.claimNext(TaskType.IDEA_ATTACHMENT_PARSE, "worker-b",
            Duration.ofSeconds(30), Duration.ofMinutes(2));
        assertThat(second).isNotNull();
        assertThat(tasks.getOwnedForWorker(run.getId()).getState()).isEqualTo(TaskRunState.RUNNING);
    }

    private TaskRun task(Context context, TaskType type, String subjectType, String subjectId) {
        String input = "{}";
        String key = UUID.randomUUID().toString();
        return tasks.create(context.owner.getId(), context.project.getId(), type, subjectType, subjectId,
            input, hasher.hash(type, "1.0", "ko-KR", input), key, key, 3);
    }

    private Context context() {
        String suffix = UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create("durable-" + suffix + "@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "durable", null, null));
        IdeaConversation conversation = conversations.create(owner.getId(), project.getId(), null);
        return new Context(owner, project, conversation);
    }

    private record Context(User owner, Project project, IdeaConversation conversation) { }
}
