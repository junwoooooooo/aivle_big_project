package com.aivle.backend.aitask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aivle.backend.aitask.application.SystemSmokeTaskCommandService;
import com.aivle.backend.aitask.application.SystemSmokeTaskJobExecutor;
import com.aivle.backend.aitask.repository.AiTaskResultRepository;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.integration.ai.AiServerException;
import com.aivle.backend.integration.ai.task.AiTaskGateway;
import com.aivle.backend.integration.ai.task.dto.AiTaskRequest;
import com.aivle.backend.integration.ai.task.dto.AiTaskResponse;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaimService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiTaskLifecycleIntegrationTests.GatewayConfiguration.class)
class AiTaskLifecycleIntegrationTests {

    @Autowired SystemSmokeTaskCommandService commands;
    @Autowired JobClaimService claims;
    @Autowired SystemSmokeTaskJobExecutor executor;
    @Autowired AnalysisJobRepository jobs;
    @Autowired AiTaskResultRepository results;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ScriptedGateway gateway;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbcClient;

    @BeforeEach
    void resetGateway() {
        gateway.failure = null;
        gateway.lastRequest = null;
    }

    @Test
    void createsClaimsRunsAndCompletesAnAnalysisJob() {
        Fixture fixture = fixture();

        var accepted = commands.start(
            fixture.userId(),
            fixture.projectId(),
            "smoke-success",
            null
        );
        var duplicate = commands.start(
            fixture.userId(),
            fixture.projectId(),
            " smoke-success ",
            null
        );

        assertThat(accepted.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(accepted.created()).isTrue();
        assertThat(duplicate.jobId()).isEqualTo(accepted.jobId());
        assertThat(duplicate.created()).isFalse();
        assertThat(jobs.findById(accepted.jobId()).orElseThrow()
            .getStatus()).isEqualTo(JobStatus.QUEUED);

        var claim = claims.claimOne(accepted.jobId())
            .orElseThrow();
        assertThat(jobs.findById(accepted.jobId()).orElseThrow()
            .getStatus()).isEqualTo(JobStatus.RUNNING);

        executor.execute(claim);

        var completed = jobs.findById(accepted.jobId())
            .orElseThrow();
        var result = results
            .findByAnalysisJobIdAndDeletedAtIsNull(
                accepted.jobId()
            )
            .orElseThrow();
        assertThat(completed.getStatus())
            .isEqualTo(JobStatus.SUCCEEDED);
        assertThat(completed.getProgress()).isEqualTo(100);
        assertThat(completed.getResultReferenceType())
            .isEqualTo("AI_TASK_RESULT");
        assertThat(completed.getResultReferenceId())
            .isEqualTo(result.getId());
        assertThat(completed.getExternalRequestId())
            .isEqualTo(gateway.lastRequest.requestId());
        assertThat(result.getSchemaVersion()).isEqualTo("1.0");
        assertThat(result.getResultJson())
            .contains("\"ok\":true");
        assertThat(gateway.lastRequest.taskId())
            .isEqualTo(accepted.jobId().toString());
    }

    @Test
    void remoteFailureIsTerminalAndKeepsSafeClassification() {
        Fixture fixture = fixture();
        var accepted = commands.start(
            fixture.userId(),
            fixture.projectId(),
            "smoke-failure",
            null
        );
        gateway.failure = new AiServerException(
            500,
            "AI_SERVER_INTERNAL_ERROR",
            true,
            "remote-request-id",
            "안전한 AI 오류",
            new RuntimeException("private stack")
        );

        executor.execute(
            claims.claimOne(accepted.jobId()).orElseThrow()
        );

        var failed = jobs.findById(accepted.jobId())
            .orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.getErrorCode())
            .isEqualTo("AI_SERVER_INTERNAL_ERROR");
        assertThat(failed.getErrorMessage())
            .isEqualTo("안전한 AI 오류");
        assertThat(failed.getRetryable()).isTrue();
        assertThat(failed.getNextAttemptAt()).isNull();
        assertThat(failed.getExternalRequestId())
            .isEqualTo("remote-request-id");
        assertThat(results
            .findByAnalysisJobIdAndDeletedAtIsNull(
                accepted.jobId()
            )).isEmpty();
    }

    @Test
    void explicitRerunCreatesNewJobAndPreservesPreviousResult() {
        Fixture fixture = fixture();
        var first = commands.start(
            fixture.userId(),
            fixture.projectId(),
            "smoke-original",
            null
        );
        executor.execute(
            claims.claimOne(first.jobId()).orElseThrow()
        );
        Long firstResultId = jobs.findById(first.jobId())
            .orElseThrow()
            .getResultReferenceId();

        var rerun = commands.start(
            fixture.userId(),
            fixture.projectId(),
            "smoke-rerun",
            first.jobId()
        );

        assertThat(rerun.jobId()).isNotEqualTo(first.jobId());
        assertThat(rerun.rerunOfJobId()).isEqualTo(first.jobId());
        assertThat(jobs.findById(first.jobId()).orElseThrow()
            .getResultReferenceId()).isEqualTo(firstResultId);
        assertThat(results.findById(firstResultId)).isPresent();

        assertThatThrownBy(() ->
            commands.start(
                fixture.userId(),
                fixture.projectId(),
                "smoke-original",
                first.jobId()
            )
        ).isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT)
        );
    }

    @Test
    void apiCreatesQueuedJobAndExistingJobApiReturnsIt()
        throws Exception {
        Fixture fixture = fixture();

        String response = mockMvc.perform(
                post(
                    "/api/v1/projects/{projectId}/ai-tasks/smoke",
                    fixture.projectId()
                )
                    .header("X-User-Id", fixture.userId())
                    .header("X-Request-Id", "api-request")
                    .header("Idempotency-Key", "api-smoke")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status").value("QUEUED"))
            .andExpect(jsonPath("$.data.created").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

        long jobId = new ObjectMapper()
            .readTree(response)
            .get("data")
            .get("jobId")
            .asLong();
        mockMvc.perform(
                get("/api/v1/jobs/{jobId}", jobId)
                    .header("X-User-Id", fixture.userId())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.data.jobType")
                    .value("SYSTEM_SMOKE_TEST")
            )
            .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void migrationProvidesRerunAndVersionedResultStorage() {
        assertThat(columnExists(
            "analysis_jobs",
            "rerun_of_job_id"
        )).isTrue();
        assertThat(columnExists(
            "ai_task_results",
            "schema_version"
        )).isTrue();
        assertThat(columnExists(
            "ai_task_results",
            "analysis_job_id"
        )).isTrue();
    }

    private boolean columnExists(
        String table,
        String column
    ) {
        Integer count = jdbcClient.sql("""
                select count(*)
                from information_schema.columns
                where lower(table_name) = :table
                  and lower(column_name) = :column
                """)
            .param("table", table)
            .param("column", column)
            .query(Integer.class)
            .single();
        return count != null && count > 0;
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(
            User.create(
                "smoke-" + suffix + "@example.com",
                "hashed",
                "smoke-user"
            )
        );
        Project project = projects.saveAndFlush(
            Project.create(
                user,
                "smoke-" + suffix,
                "AI task lifecycle",
                "test"
            )
        );
        return new Fixture(user.getId(), project.getId());
    }

    record Fixture(Long userId, Long projectId) {
    }

    @TestConfiguration
    static class GatewayConfiguration {
        @Bean
        @Primary
        ScriptedGateway scriptedGateway(
            ObjectMapper objectMapper
        ) {
            return new ScriptedGateway(objectMapper);
        }
    }

    static class ScriptedGateway implements AiTaskGateway {
        private final ObjectMapper objectMapper;
        private AiTaskRequest lastRequest;
        private AiServerException failure;

        ScriptedGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiTaskResponse execute(AiTaskRequest request) {
            lastRequest = request;
            if (failure != null) {
                throw failure;
            }
            return new AiTaskResponse(
                request.requestId(),
                request.taskId(),
                request.taskType(),
                "SUCCEEDED",
                request.schemaVersion(),
                objectMapper.createObjectNode().put("ok", true),
                List.of(),
                new AiTaskResponse.Execution(
                    "system-smoke",
                    "1.0"
                ),
                null
            );
        }
    }
}
