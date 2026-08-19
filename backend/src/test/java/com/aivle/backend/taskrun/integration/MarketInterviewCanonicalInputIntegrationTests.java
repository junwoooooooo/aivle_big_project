package com.aivle.backend.taskrun.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.common.entity.ProjectStatus;
import com.aivle.backend.common.exception.GlobalExceptionHandler;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketinterview.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.refinement.ConceptRefinementFinal;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRound;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskAttemptRepository;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import com.aivle.backend.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MarketInterviewCanonicalInputIntegrationTests {
    @Test
    void sampleSizesCreateRealTaskRunsWithMainBusinessInput() {
        Harness harness = new Harness();
        Set<String> hashes = new HashSet<>();

        for (int sampleSize : List.of(20, 40, 80)) {
            String key = "sample-" + sampleSize;
            harness.marketInterviews.start(7L, 41L, key, "request-" + sampleSize, sampleSize);
            TaskRun run = harness.taskRun(key);
            JsonNode input = harness.mapper.readTree(run.getInputSnapshot());

            assertThat(run.getContractVersion()).isEqualTo("1.0");
            assertThat(run.getTaskSchemaVersion()).isEqualTo("1.0");
            assertThat(run.getLocale()).isEqualTo("ko-KR");
            assertThat(input.propertyNames()).containsExactlyInAnyOrder("conceptBoard", "sampleSize");
            assertThat(input.path("sampleSize").asInt()).isEqualTo(sampleSize);
            assertThat(run.getInputHash()).isEqualTo(harness.hasher.hash(
                TaskType.MARKET_INTERVIEW, "1.0", "ko-KR", run.getInputSnapshot()));
            hashes.add(run.getInputHash());
        }

        assertThat(hashes).hasSize(3);
    }

    @Test
    void internalExecutionEnvelopeUsesThePersistedTaskV1SchemaAndHash() {
        Harness harness = new Harness();
        harness.marketInterviews.start(7L, 41L, "internal-envelope", "request-envelope", 20);
        TaskRun run = harness.taskRun("internal-envelope");
        TaskRunWorkerContext context = new TaskRunWorkerContext(
            run.getId(), 41L, 7L, run.getTaskType(), run.getSubjectType(), run.getSubjectId(),
            run.getInputSnapshot(), run.getInputHash(), run.getIdempotencyKey(), run.getCorrelationId(),
            run.getContractVersion(), run.getTaskSchemaVersion(), run.getLocale(),
            run.getAttemptCount(), run.getMaxAttempts());
        InternalAiExecutionClient client = new InternalAiExecutionClient(
            mock(RestClient.class), mock(AiServerProperties.class), harness.mapper);

        JsonNode request = client.requestPayload(
            context, "attempt-1", LocalDateTime.of(2026, 8, 17, 12, 0));

        assertThat(request.path("contractVersion").asText()).isEqualTo("1.0");
        assertThat(request.path("taskSchemaVersion").asText()).isEqualTo("1.0");
        assertThat(request.path("locale").asText()).isEqualTo("ko-KR");
        assertThat(request.path("canonicalInputHash").asText()).isEqualTo(run.getInputHash());
        assertThat(request.path("input").propertyNames())
            .containsExactlyInAnyOrder("conceptBoard", "sampleSize");
        assertThat(request.path("input").path("sampleSize").asInt()).isEqualTo(20);
    }

    @Test
    void sameKeyAndCanonicalInputReplaysButChangedSampleSizeKeepsRequestHashMismatch() {
        Harness harness = new Harness();
        harness.marketInterviews.start(7L, 41L, "same-key", "request-1", 20);
        TaskRun first = harness.taskRun("same-key");

        harness.marketInterviews.start(7L, 41L, "same-key", "request-2", 20);

        assertThat(harness.taskRun("same-key")).isSameAs(first);
        assertThat(harness.savedTaskRunCount).isEqualTo(1);
        assertThatThrownBy(() -> harness.marketInterviews.start(
                7L, 41L, "same-key", "request-3", 40))
            .isInstanceOfSatisfying(TaskRunFailure.class, failure -> {
                assertThat(failure.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
                assertThat(failure.getReason()).isEqualTo("REQUEST_HASH_MISMATCH");
            });
    }

    @Test
    void browserPostWithSampleSizeTwentyCannotReachCanonicalHashMismatch() throws Exception {
        Harness harness = new Harness();
        CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
        when(currentUser.currentUserId()).thenReturn(7L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new MarketInterviewController(harness.marketInterviews, currentUser))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        mvc.perform(post("/api/v3/projects/41/market-interview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sampleSize":20,"conceptBoard":{"conceptName":"예약 도우미",
                    "targetUsers":"서울 매장","problemScenario":"예약 누락","featureSet":["예약 확인"],
                    "differentiators":"누락 방지","priceKrw":9900}}
                    """)
                .header("Idempotency-Key", "browser-20")
                .requestAttr("requestId", "request-browser-20"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.requestedSampleSize").value(20));

        assertThat(harness.taskRun("browser-20").getInputHash()).startsWith("sha256:");
    }

    private static final class Harness {
        private final ObjectMapper mapper = new ObjectMapper();
        private final CanonicalInputHasher hasher = new CanonicalInputHasher(mapper);
        private final Map<String, TaskRun> taskRunsByKey = new HashMap<>();
        private final Map<String, MarketInterviewRun> interviewRunsByTaskId = new HashMap<>();
        private int savedTaskRunCount;
        private final MarketInterviewService marketInterviews;

        private Harness() {
            ProjectRepository projects = mock(ProjectRepository.class);
            Project project = mock(Project.class);
            User owner = mock(User.class);
            when(project.getId()).thenReturn(41L);
            when(project.getOwner()).thenReturn(owner);
            when(project.getStatus()).thenReturn(ProjectStatus.ACTIVE);
            when(owner.getId()).thenReturn(7L);
            when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
            when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project));

            TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
            when(taskRunRepository.findByProjectIdAndIdempotencyScopeAndIdempotencyKey(
                    eq(41L), anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                    taskRunsByKey.get(invocation.getArgument(2, String.class))));
            when(taskRunRepository.findFirstByProjectIdAndTaskTypeAndSubjectTypeAndSubjectIdAndInputHashAndStateIn(
                    eq(41L), eq(TaskType.MARKET_INTERVIEW), anyString(), anyString(), anyString(),
                    anyList()))
                .thenReturn(Optional.empty());
            when(taskRunRepository.save(any(TaskRun.class))).thenAnswer(invocation -> {
                TaskRun run = invocation.getArgument(0);
                taskRunsByKey.put(run.getIdempotencyKey(), run);
                savedTaskRunCount++;
                return run;
            });
            TaskRunService taskRuns = new TaskRunService(
                taskRunRepository, mock(TaskAttemptRepository.class), mock(TaskResultRepository.class),
                projects, Optional.of(Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC)),
                mapper, hasher, mock(ServicePolicyService.class));

            ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
            when(selection.getId()).thenReturn(31L);
            when(selection.getHypothesisRevision()).thenReturn(4);
            MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
            when(seed.getId()).thenReturn("seed-1");
            when(seed.getSnapshotHash()).thenReturn("sha256:" + "a".repeat(64));
            when(seed.getSnapshotJson()).thenReturn("""
                {"contract":"market-analysis-seed-snapshot-v1","schemaVersion":"2.0",
                 "selectedConcept":{"identity":{"name":"예약 도우미","targetUsers":"서울 매장"}},
                 "finalHypotheses":{}}
                """);
            var plan = new BmPlanPreparationService.PlanView(
                mapper.createObjectNode(), mapper.createObjectNode(), 3);
            ConceptRefinementFinal refinementFinal = mock(ConceptRefinementFinal.class);
            ConceptRefinementRound refinementRound = mock(ConceptRefinementRound.class);
            when(refinementFinal.getId()).thenReturn(17L);
            JsonNode finalDocument = mapper.readTree("""
                {"selectedConcept":{"identity":{"conceptName":"예약 도우미","targetUsers":"서울 매장"},
                  "solution":{"problemScenario":"예약 누락","featureSet":["예약 확인"]}},
                 "finalHypotheses":{"differentiators":{"value":"누락 방지"},"price":{"value":9900}}}
                """);
            MarketInterviewSourceResolver sources = mock(MarketInterviewSourceResolver.class);
            when(sources.require(41L)).thenReturn(new MarketInterviewSourceResolver.Source(
                refinementFinal, refinementRound, seed, selection, plan, finalDocument));

            MarketInterviewRunRepository interviewRuns = mock(MarketInterviewRunRepository.class);
            when(interviewRuns.save(any(MarketInterviewRun.class))).thenAnswer(invocation -> {
                MarketInterviewRun run = invocation.getArgument(0);
                interviewRunsByTaskId.put(run.getTaskRun().getId(), run);
                return run;
            });
            when(interviewRuns.findByTaskRunIdAndDeletedAtIsNull(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                    interviewRunsByTaskId.get(invocation.getArgument(0, String.class))));
            marketInterviews = new MarketInterviewService(
                projects, sources, new MarketInterviewInputFactory(
                    mapper, new com.aivle.backend.pipeline.market.MarketInterviewInputFactory(mapper)),
                interviewRuns, mock(com.aivle.backend.taskrun.repository.TaskResultRepository.class),
                taskRuns, hasher, mapper);
        }

        private TaskRun taskRun(String idempotencyKey) {
            return Objects.requireNonNull(taskRunsByKey.get(idempotencyKey));
        }
    }
}
