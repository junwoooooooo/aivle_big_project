package com.aivle.backend.journey.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.aivle.backend.jobevent.JobEventRepository;
import com.aivle.backend.postgres.PostgreSqlIntegrationTestSupport;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Tag("postgres")
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
class PostgreSqlIdeaIntakeWorkerTests extends PostgreSqlIntegrationTestSupport {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired IdeaWorkspaceService workspaces;
    @Autowired IdeaIntakeClaimService claims;
    @Autowired IdeaIntakeDurableWorker worker;
    @Autowired TaskRunService tasks;
    @Autowired CanonicalInputHasher inputHasher;
    @Autowired IdeaConversationRepository conversations;
    @Autowired IdeaMessageRepository messages;
    @Autowired JobEventRepository events;
    @Autowired ObjectMapper mapper;
    @MockitoBean InternalAiExecutionClient ai;

    @Test
    void detachedClaimCompletesAssistantQuestionBriefAndTerminalEvent() {
        Context context = context();
        var accepted = workspaces.send(context.ownerId(), context.projectId(), context.conversationId(),
            "customer disposal problem", List.of());
        when(ai.executeWorker(any(TaskRunWorkerContext.class), anyString(), any(LocalDateTime.class)))
            .thenAnswer(invocation -> response(invocation.getArgument(0), invocation.getArgument(1),
                result("NEEDS_INPUT")));

        IdeaIntakeClaimService.ClaimContext claim = claims.claimNext(TaskType.IDEA_CONVERSATION_TURN,
            "postgres-worker", Duration.ofMinutes(5), Duration.ofMinutes(3));
        assertThat(claim.task().ownerId()).isEqualTo(context.ownerId());
        assertThat(claim.conversationId()).isEqualTo(context.conversationId());
        JsonNode taskInput = mapper.readTree(claim.task().inputSnapshot());
        assertThat(taskInput.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(taskInput.path("conversationContract").asText()).isEqualTo("opportunity-brief-v1");
        assertThat(taskInput.path("projectId").asLong()).isEqualTo(context.projectId());
        assertThat(taskInput.path("ownerId").asLong()).isEqualTo(context.ownerId());
        assertThat(taskInput.path("conversationId").asLong()).isEqualTo(context.conversationId());
        assertThat(taskInput.path("sourceMessageId").asLong()).isEqualTo(accepted.message().id());
        assertThat(taskInput.path("currentBrief").isNull()).isTrue();
        assertThat(taskInput.path("attachments").isEmpty()).isTrue();
        assertThat(taskInput.path("messages").get(0).path("envelope").isNull()).isTrue();
        worker.processClaim(claim);

        var workspace = workspaces.current(context.ownerId(), context.projectId());
        assertThat(workspace.messages()).hasSize(2);
        assertThat(workspace.messages().get(1).type()).isEqualTo(IdeaMessageContract.Type.QUESTION_SET);
        assertThat(workspace.messages().get(1).occurredAt()).endsWith("Z");
        assertThat(workspace.brief()).isNotNull();
        assertThat(workspace.brief().fields()).allSatisfy(field -> {
            assertThat(field.userConfirmed()).isFalse();
            assertThat(field.sourceType().name()).isIn("AI_PROPOSED", "SOURCE_EXTRACTED", "MISSING");
        });
        assertThat(workspace.domainState()).isEqualTo("NEEDS_INPUT");
        assertThat(workspace.activeJobId()).isNull();
        assertThat(tasks.workerContext(accepted.jobId()).taskRunId()).isEqualTo(accepted.jobId());
        assertThat(tasks.getOwnedForWorker(accepted.jobId()).getState()).isEqualTo(TaskRunState.SUCCEEDED);
        var terminal = events.findTopByJobIdAndProjectIdAndDeletedAtIsNullOrderBySequenceDesc(
            accepted.jobId(), context.projectId()).orElseThrow();
        assertThat(terminal.getMessageKey()).isEqualTo("job.idea.questions.completed");
        assertThat(terminal.getEventType()).isEqualTo("job.completed");
        assertThat(terminal.getMessageParamsJson()).doesNotContain("customer disposal problem", "providerBody");

        var followUp = workspaces.send(context.ownerId(), context.projectId(), context.conversationId(),
            "Seoul apartment residents", List.of());
        IdeaIntakeClaimService.ClaimContext followUpClaim = claims.claimNext(
            TaskType.IDEA_CONVERSATION_TURN, "postgres-worker-follow-up",
            Duration.ofMinutes(5), Duration.ofMinutes(3));
        JsonNode followUpInput = mapper.readTree(followUpClaim.task().inputSnapshot());
        assertThat(followUpInput.path("sourceMessageId").asLong()).isEqualTo(followUp.message().id());
        assertThat(followUpInput.path("briefVersionId").isIntegralNumber()).isTrue();
        assertThat(followUpInput.path("currentBrief").isObject()).isTrue();
        assertThat(followUpInput.path("messages").get(1).path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(followUpInput.path("messages").get(1).path("envelope")
            .path("schemaVersion").asText()).isEqualTo("1.0");
        worker.processClaim(followUpClaim);
        assertThat(tasks.getOwnedForWorker(followUp.jobId()).getState()).isEqualTo(TaskRunState.SUCCEEDED);
    }

    @Test
    void sufficientResultCreatesBriefReviewReadyForConfirmation() {
        Context context = context();
        var accepted = workspaces.send(context.ownerId(), context.projectId(), context.conversationId(),
            "complete business input", List.of());
        when(ai.executeWorker(any(TaskRunWorkerContext.class), anyString(), any(LocalDateTime.class)))
            .thenAnswer(invocation -> response(invocation.getArgument(0), invocation.getArgument(1),
                result("READY_FOR_CONFIRMATION")));

        worker.processClaim(claims.claimNext(TaskType.IDEA_CONVERSATION_TURN,
            "postgres-ready", Duration.ofMinutes(5), Duration.ofMinutes(3)));

        var workspace = workspaces.current(context.ownerId(), context.projectId());
        assertThat(workspace.messages().get(1).type()).isEqualTo(IdeaMessageContract.Type.BRIEF_REVIEW);
        assertThat(workspace.domainState()).isEqualTo("READY_FOR_CONFIRMATION");
        assertThat(workspace.activeJobId()).isNull();
        assertThat(events.findTopByJobIdAndProjectIdAndDeletedAtIsNullOrderBySequenceDesc(
            accepted.jobId(), context.projectId()).orElseThrow().getMessageKey())
            .isEqualTo("job.idea.brief.draft.completed");
    }

    @Test
    void repairedProviderResultPersistsOneAssistantOneBriefAndSafeRepairEvent() {
        Context context = context();
        var accepted = workspaces.send(context.ownerId(), context.projectId(), context.conversationId(),
            "repair result input", List.of());
        when(ai.executeWorker(any(TaskRunWorkerContext.class), anyString(), any(LocalDateTime.class)))
            .thenAnswer(invocation -> responseWithRepair(invocation.getArgument(0), invocation.getArgument(1),
                result("NEEDS_INPUT"), 5));

        worker.processClaim(claims.claimNext(TaskType.IDEA_CONVERSATION_TURN,
            "postgres-repair", Duration.ofMinutes(5), Duration.ofMinutes(3)));

        var workspace = workspaces.current(context.ownerId(), context.projectId());
        assertThat(workspace.messages()).hasSize(2);
        assertThat(workspace.brief()).isNotNull();
        assertThat(tasks.getOwnedForWorker(accepted.jobId()).getState()).isEqualTo(TaskRunState.SUCCEEDED);
        var jobEvents = events.findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
            accepted.jobId(), context.projectId(), 0);
        assertThat(jobEvents).filteredOn(event -> "job.idea.result.repairing".equals(event.getEventType()))
            .singleElement().satisfies(event -> {
                assertThat(event.getMessageParamsJson()).contains("REPAIR", "5");
                assertThat(event.getMessageParamsJson()).doesNotContain("repair result input", "providerBody");
            });
        assertThat(jobEvents.get(jobEvents.size() - 1).getEventType()).isEqualTo("job.completed");
    }

    @Test
    void unknownRuntimeFailureNeverLeavesTaskRunning() {
        Context context = context();
        var accepted = workspaces.send(context.ownerId(), context.projectId(), context.conversationId(),
            "runtime failure input", List.of());
        when(ai.executeWorker(any(TaskRunWorkerContext.class), anyString(), any(LocalDateTime.class)))
            .thenThrow(new IllegalStateException("raw provider body must not escape"));

        worker.processClaim(claims.claimNext(TaskType.IDEA_CONVERSATION_TURN,
            "postgres-failure", Duration.ofMinutes(5), Duration.ofMinutes(3)));

        assertThat(tasks.getOwnedForWorker(accepted.jobId()).getState()).isEqualTo(TaskRunState.FAILED);
        var workspace = workspaces.current(context.ownerId(), context.projectId());
        assertThat(workspace.domainState()).isEqualTo("FAILED");
        assertThat(workspace.activeJobId()).isNull();
        var terminal = events.findTopByJobIdAndProjectIdAndDeletedAtIsNullOrderBySequenceDesc(
            accepted.jobId(), context.projectId()).orElseThrow();
        assertThat(terminal.getStatus().name()).isEqualTo("FAILED");
        assertThat(terminal.getMessageParamsJson()).doesNotContain("raw provider body");
    }

    @Test
    void retryableProviderFailureIsBoundedByMaxAttempts() throws Exception {
        Context context = context();
        var accepted = workspaces.send(context.ownerId(), context.projectId(), context.conversationId(),
            "retry input", List.of());
        when(ai.executeWorker(any(TaskRunWorkerContext.class), anyString(), any(LocalDateTime.class)))
            .thenThrow(new ExecutionFailure("DEPENDENCY_UNAVAILABLE",
                "MODEL_DEPENDENCY_UNAVAILABLE", true));

        for (int attempt = 1; attempt <= 3; attempt++) {
            IdeaIntakeClaimService.ClaimContext claim = claims.claimNext(
                TaskType.IDEA_CONVERSATION_TURN, "postgres-retry-" + attempt,
                Duration.ofMinutes(5), Duration.ofMinutes(3));
            assertThat(claim).isNotNull();
            worker.processClaim(claim);
            if (attempt < 3) Thread.sleep((1L << (attempt - 1)) * 1_000L);
        }

        var run = tasks.getOwnedForWorker(accepted.jobId());
        assertThat(run.getState()).isEqualTo(TaskRunState.FAILED);
        assertThat(run.getAttemptCount()).isEqualTo(3);
        assertThat(run.isRetryable()).isFalse();
        assertThat(workspaces.current(context.ownerId(), context.projectId()).activeJobId()).isNull();
    }

    @Test
    void requestContractFailureIsPermanentAndIsNotRetried() {
        Context context = context();
        var accepted = workspaces.send(context.ownerId(), context.projectId(), context.conversationId(),
            "contract failure input", List.of());
        when(ai.executeWorker(any(TaskRunWorkerContext.class), anyString(), any(LocalDateTime.class)))
            .thenThrow(new ExecutionFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", false));

        worker.processClaim(claims.claimNext(TaskType.IDEA_CONVERSATION_TURN,
            "postgres-contract-failure", Duration.ofMinutes(5), Duration.ofMinutes(3)));

        var run = tasks.getOwnedForWorker(accepted.jobId());
        assertThat(run.getState()).isEqualTo(TaskRunState.FAILED);
        assertThat(run.getAttemptCount()).isEqualTo(1);
        assertThat(run.isRetryable()).isFalse();
        assertThat(workspaces.current(context.ownerId(), context.projectId()).activeJobId()).isNull();
    }

    @Test
    void providerResponseSchemaFailureIsPermanentAndIsNotRetried() {
        Context context = context();
        var accepted = workspaces.send(context.ownerId(), context.projectId(), context.conversationId(),
            "invalid result input", List.of());
        when(ai.executeWorker(any(TaskRunWorkerContext.class), anyString(), any(LocalDateTime.class)))
            .thenThrow(new ExecutionFailure("RESULT_SCHEMA_INVALID", "PROVIDER_RESPONSE_SCHEMA_REJECTED", false));

        worker.processClaim(claims.claimNext(TaskType.IDEA_CONVERSATION_TURN,
            "postgres-result-schema-failure", Duration.ofMinutes(5), Duration.ofMinutes(3)));

        var run = tasks.getOwnedForWorker(accepted.jobId());
        assertThat(run.getState()).isEqualTo(TaskRunState.FAILED);
        assertThat(run.getAttemptCount()).isEqualTo(1);
        assertThat(run.isRetryable()).isFalse();
        assertThat(workspaces.current(context.ownerId(), context.projectId()).activeJobId()).isNull();
        var terminal = events.findTopByJobIdAndProjectIdAndDeletedAtIsNullOrderBySequenceDesc(
            accepted.jobId(), context.projectId()).orElseThrow();
        assertThat(terminal.getTechnicalCode()).isEqualTo("RESULT_SCHEMA_INVALID");
        assertThat(terminal.getMessageParamsJson()).doesNotContain("provider", "response_format", "invalid result input");
    }

    @Test
    void claimTransactionRejectsAMessageFromAnotherProjectAndRollsBackTheClaim() {
        Context ownerContext = context();
        Context foreignContext = context();
        IdeaConversation foreignConversation = conversations.findById(foreignContext.conversationId())
            .orElseThrow();
        IdeaMessage foreignMessage = messages.saveAndFlush(IdeaMessage.create(
            foreignConversation, 1, IdeaMessage.Role.USER, "foreign project input"));
        String input = "{}";
        String hash = inputHasher.hash(TaskType.IDEA_CONVERSATION_TURN, "1.0", "ko-KR", input);
        var task = tasks.create(ownerContext.ownerId(), ownerContext.projectId(),
            TaskType.IDEA_CONVERSATION_TURN, "IDEA_MESSAGE", foreignMessage.getId().toString(),
            input, hash, "cross-project-" + UUID.randomUUID(), UUID.randomUUID().toString(), 3);

        assertThatThrownBy(() -> claims.claimNext(TaskType.IDEA_CONVERSATION_TURN,
            "postgres-isolation", Duration.ofMinutes(5), Duration.ofMinutes(3)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("conversation task project mismatch");

        assertThat(tasks.getOwnedForWorker(task.getId()).getState()).isEqualTo(TaskRunState.QUEUED);
        assertThat(tasks.getOwnedForWorker(task.getId()).getAttemptCount()).isZero();
    }

    private ExecutionResponse response(TaskRunWorkerContext run, String attemptId, JsonNode result) {
        return new ExecutionResponse(run.contractVersion(), run.taskType().name(), run.taskSchemaVersion(),
            run.taskRunId(), attemptId, run.correlationId(), run.inputHash(), "1.0", result,
            mapper.createArrayNode(), mapper.createArrayNode(), null);
    }

    private ExecutionResponse responseWithRepair(TaskRunWorkerContext run, String attemptId,
            JsonNode result, int issueCount) {
        ArrayNode warnings = mapper.createArrayNode();
        warnings.addObject().put("code", "RESULT_SCHEMA_REPAIRED")
            .put("attemptPhase", "REPAIR").put("issueCount", issueCount);
        return new ExecutionResponse(run.contractVersion(), run.taskType().name(), run.taskSchemaVersion(),
            run.taskRunId(), attemptId, run.correlationId(), run.inputHash(), "1.0", result,
            warnings, mapper.createArrayNode(), null);
    }

    private ObjectNode result(String readiness) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode fields = root.putArray("extractedFields");
        addField(fields, "problem", "waste problem");
        addField(fields, "targetCustomer", "apartment residents");
        addField(fields, "desiredOutcome", "less waste");
        if (!"NEEDS_INPUT".equals(readiness)) addField(fields, "targetRegion", "Seoul");
        addField(fields, "fixedConstraints", "partner operation");
        addField(fields, "openDecisions", "reward model");
        addField(fields, "regulatorySensitiveActivities", "collection and location data");
        root.putArray("fieldSuggestions");
        root.putArray("assumptions");
        ArrayNode openFields = root.putArray("openFields");
        if ("NEEDS_INPUT".equals(readiness)) openFields.add("targetRegion");
        root.putArray("contradictions");
        ArrayNode questions = root.putArray("clarificationQuestions");
        if ("NEEDS_INPUT".equals(readiness)) {
            addQuestion(questions, "q1", "usageContext", "When will it be used?");
            addQuestion(questions, "q2", "preferredConstraints", "Which channel is preferred?");
        }
        root.put("readiness", readiness);
        root.put("userFacingSummary", "We organized the business opportunity.");
        return root;
    }

    private void addField(ArrayNode fields, String key, String value) {
        ObjectNode field = fields.addObject();
        field.put("fieldKey", key);
        field.put("valueJson", value);
        field.put("decisionStatus", "PREFERRED");
        field.put("sourceType", "AI_PROPOSED");
        field.put("confidence", 0.8);
    }

    private void addQuestion(ArrayNode questions, String id, String field, String prompt) {
        ObjectNode question = questions.addObject();
        question.put("id", id);
        question.put("fieldKey", field);
        question.put("prompt", prompt);
        question.put("type", "FREE_TEXT");
        question.putArray("options");
        question.put("allowUndecided", true);
    }

    private Context context() {
        String suffix = UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create("intake-fix-" + suffix + "@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "runtime-fix", null, null));
        var workspace = workspaces.create(owner.getId(), project.getId(), false);
        return new Context(owner.getId(), project.getId(), workspace.id());
    }

    private record Context(Long ownerId, Long projectId, Long conversationId) { }
}
