package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorker;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.domain.TaskResultValidationState;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

// V29 contains a PostgreSQL partial index that H2 cannot parse. Migration compatibility is
// covered separately; this worker test needs an isolated entity schema only.
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class TaskRunWorkerIntegrationTests {
    @Autowired TaskRunService service;
    @Autowired TaskRunWorker worker;
    @Autowired CanonicalInputHasher hasher;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ObjectMapper mapper;
    @Autowired TaskResultRepository results;
    @MockitoBean InternalAiExecutionClient client;

    @Test
    void executesAiOutsideTransactionAndAdoptsValidatedResult() {
        String suffix = java.util.UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create("worker-" + suffix + "@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "worker skeleton", null, null));
        String input = "{}";
        String hash = hasher.hash(TaskType.IDEA_INTERPRETATION, "1.0", "ko-KR", input);
        TaskRun run = service.create(owner.getId(), project.getId(), TaskType.IDEA_INTERPRETATION,
            "IDEA_INTERPRETATION_RUN", "worker-" + suffix, input, hash, "key-" + suffix,
            "correlation-" + suffix, 3);

        when(client.execute(any(), anyString(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            TaskRun executing = invocation.getArgument(0);
            String attemptId = invocation.getArgument(1);
            var provenance = mapper.valueToTree(List.of(java.util.Map.of(
                "category", "AI_PROPOSAL", "statementKey", "proposal-1", "sourceKeys", List.of(),
                "externalSourceReferences", List.of(), "generatedAt", "2035-01-01T00:00:00Z",
                "verificationNeeded", true)));
            var result = mapper.valueToTree(java.util.Map.of(
                "originalSourceSummary", "input summary", "normalizedDescription", "normalized idea",
                "facts", List.of(), "assumptions", List.of(), "constraints", List.of(),
                "openQuestions", List.of(), "readiness", "APPROPRIATE", "warnings", List.of(),
                "evidenceNeeds", List.of(), "provenance", provenance));
            return new ExecutionResponse("1.0", executing.getTaskType().name(), "1.0", executing.getId(),
                attemptId, executing.getCorrelationId(), executing.getInputHash(), "1.0", result,
                mapper.createArrayNode(), provenance, null);
        });

        assertThat(worker.executeOne("worker-1")).isTrue();
        TaskRun completed = service.getOwned(owner.getId(), project.getId(), run.getId());
        assertThat(completed.getState()).isEqualTo(TaskRunState.SUCCEEDED);
        assertThat(completed.getFinalResultId()).isNotBlank();
    }

    @Test
    void invalidStructuredResultIsRejectedAndCannotBecomeFinalResult() {
        String suffix = java.util.UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create("invalid-worker-" + suffix + "@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "invalid worker result", null, null));
        String input = "{}";
        String hash = hasher.hash(TaskType.IDEA_INTERPRETATION, "1.0", "ko-KR", input);
        TaskRun run = service.create(owner.getId(), project.getId(), TaskType.IDEA_INTERPRETATION,
            "IDEA_INTERPRETATION_RUN", "invalid-worker-" + suffix, input, hash, "invalid-key-" + suffix,
            "invalid-correlation-" + suffix, 3);

        when(client.execute(any(), anyString(), any())).thenAnswer(invocation -> {
            TaskRun executing = invocation.getArgument(0);
            String attemptId = invocation.getArgument(1);
            return new ExecutionResponse("1.0", executing.getTaskType().name(), "1.0", executing.getId(),
                attemptId, executing.getCorrelationId(), executing.getInputHash(), "1.0",
                mapper.createObjectNode().put("readiness", "APPROPRIATE"), mapper.createArrayNode(),
                mapper.createArrayNode(), null);
        });

        assertThat(worker.executeOne("worker-invalid")).isTrue();
        TaskRun failed = service.getOwned(owner.getId(), project.getId(), run.getId());
        assertThat(failed.getState()).isEqualTo(TaskRunState.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("AI_RESULT_INVALID");
        assertThat(failed.getFinalResultId()).isNull();
        assertThat(results.findByTaskRunId(run.getId())).singleElement()
            .extracting(result -> result.getValidationState())
            .isEqualTo(TaskResultValidationState.REJECTED);
    }
}
