package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.aivle.backend.jobevent.JobEventRepository;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchRunRepository;
import com.aivle.backend.pipeline.market.MarketResearchService;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.market.MarketResearchWorker;
import com.aivle.backend.pipeline.market.ledger.MarketLedgerArtifactService;
import com.aivle.backend.pipeline.market.TwinStimulusDraftWorker;
import com.aivle.backend.pipeline.market.TwinSurveyRun;
import com.aivle.backend.pipeline.market.TwinSurveyRunRepository;
import com.aivle.backend.pipeline.market.TwinSurveyVersionRepository;
import com.aivle.backend.pipeline.market.TwinSurveyWorker;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:transplanted-workers;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@ActiveProfiles("test")
class TransplantedWorkerIntegrationTests {
    @Autowired TaskRunService taskRuns;
    @Autowired CanonicalInputHasher hasher;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired MarketResearchRunRepository marketRuns;
    @Autowired MarketResearchVersionRepository marketVersions;
    @Autowired TwinSurveyRunRepository twinRuns;
    @Autowired TwinSurveyVersionRepository twinVersions;
    @Autowired MarketResearchService marketService;
    @Autowired MarketResearchWorker marketWorker;
    @Autowired MarketLedgerArtifactService marketLedgerArtifacts;
    @Autowired TwinStimulusDraftWorker draftWorker;
    @Autowired TwinSurveyWorker twinWorker;
    @Autowired TaskResultRepository results;
    @Autowired JobEventRepository events;
    @Autowired ObjectMapper mapper;
    @MockitoBean InternalAiExecutionClient client;

    @Test
    void marketWorkerAtomicallyAdoptsAndMaterializesExactlyOneVersionThenGetStaysReadOnly() throws Exception {
        Context context = context("market");
        String input = "{\"conceptId\":\"concept-1\",\"asOf\":\"2026-08-13\","
            + "\"source\":{\"projectId\":" + context.project.getId()
            + ",\"selectedConceptHash\":\"sha256:" + "a".repeat(64) + "\"}}";
        TaskRun task = queue(context, TaskType.MARKET_RESEARCH, "MARKET_RESEARCH_FULL", "concept-1", input);
        marketRuns.saveAndFlush(MarketResearchRun.create(
            context.project, MarketResearchRun.Kind.FULL, null, task, task.getInputHash()));
        stub(fixture("market_research/full.json"));

        assertThat(marketWorker.processOne()).isTrue();
        assertThat(taskRuns.getOwned(context.owner.getId(), context.project.getId(), task.getId()).getState())
            .isEqualTo(TaskRunState.SUCCEEDED);
        assertThat(marketVersions.countByProjectIdAndKindAndDeletedAtIsNull(
            context.project.getId(), MarketResearchRun.Kind.FULL)).isOne();

        marketService.current(context.owner.getId(), context.project.getId(), MarketResearchRun.Kind.FULL);
        marketService.current(context.owner.getId(), context.project.getId(), MarketResearchRun.Kind.FULL);
        assertThat(marketVersions.countByProjectIdAndKindAndDeletedAtIsNull(
            context.project.getId(), MarketResearchRun.Kind.FULL)).isOne();
        assertThat(events.findTopByJobIdAndDeletedAtIsNullOrderBySequenceDesc(task.getId())).isPresent();
        assertThat(marketWorker.processOne()).isFalse();
    }

    @Test
    void invalidMarketResultFailsWithoutCreatingAVersion() throws Exception {
        Context context = context("market-failure");
        TaskRun task = queue(context, TaskType.MARKET_RESEARCH, "MARKET_RESEARCH_BM", "concept-2", "{}");
        marketRuns.saveAndFlush(MarketResearchRun.create(
            context.project, MarketResearchRun.Kind.BM, null, task, task.getInputHash()));
        ObjectNode broken = (ObjectNode) fixture("market_research/bm.json");
        ((tools.jackson.databind.node.ArrayNode) broken.path("canvas").path("cells").path(0).path("caveats"))
            .removeAll();
        stub(broken);

        assertThat(marketWorker.processOne()).isTrue();
        assertThat(taskRuns.getOwned(context.owner.getId(), context.project.getId(), task.getId()).getState())
            .isEqualTo(TaskRunState.FAILED);
        assertThat(marketVersions.countByProjectIdAndKindAndDeletedAtIsNull(
            context.project.getId(), MarketResearchRun.Kind.BM)).isZero();
    }

    @Test
    void twinDraftAndSurveyUseSeparateTaskRunsAndSurveyMaterializesOnce() throws Exception {
        Context context = context("twin");
        TaskRun draft = queue(context, TaskType.TWIN_STIMULUS_DRAFT,
            "TWIN_STIMULUS_DRAFT", String.valueOf(context.project.getId()), "{}");
        stub(fixture("twin_survey/stimulus_draft.json"));
        assertThat(draftWorker.processOne()).isTrue();
        assertThat(taskRuns.getOwned(context.owner.getId(), context.project.getId(), draft.getId()).getState())
            .isEqualTo(TaskRunState.SUCCEEDED);

        TaskRun survey = queue(context, TaskType.TWIN_SURVEY,
            "TWIN_SURVEY", String.valueOf(context.project.getId()), "{}");
        twinRuns.saveAndFlush(TwinSurveyRun.create(context.project, survey, survey.getInputHash(), 50,
            "seed-lineage", 17L, 3));
        stub(fixture("twin_survey/survey.json"));
        assertThat(twinWorker.processOne()).isTrue();

        assertThat(taskRuns.getOwned(context.owner.getId(), context.project.getId(), survey.getId()).getState())
            .isEqualTo(TaskRunState.SUCCEEDED);
        assertThat(twinVersions.countByProjectIdAndDeletedAtIsNull(context.project.getId())).isOne();
        assertThat(twinWorker.processOne()).isFalse();
        assertThat(results.findByTaskRunId(survey.getId()))
            .extracting(com.aivle.backend.taskrun.domain.TaskResult::getValidationState)
            .containsExactly(com.aivle.backend.taskrun.domain.TaskResultValidationState.ADOPTED);
    }

    private Context context(String label) {
        String suffix = UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create(label + "-" + suffix + "@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, label, null, null));
        return new Context(owner, project, suffix);
    }

    private TaskRun queue(Context context, TaskType type, String subjectType, String subjectId, String input) {
        String hash = hasher.hash(type, "1.0", "ko-KR", input);
        return taskRuns.create(context.owner.getId(), context.project.getId(), type, subjectType, subjectId,
            input, hash, "key-" + context.suffix + "-" + type, "correlation-" + context.suffix, 1);
    }

    private void stub(JsonNode result) {
        doAnswer(invocation -> {
            TaskRun executing = invocation.getArgument(0);
            String attemptId = invocation.getArgument(1);
            if (executing.getTaskType() == TaskType.MARKET_RESEARCH
                    && "MARKET_RESEARCH_FULL".equals(executing.getSubjectType())) {
                marketLedgerArtifacts.stage(executing.getId(), attemptId,
                    ledgerBundle(executing, attemptId, result));
            }
            return new ExecutionResponse("1.0", executing.getTaskType().name(), "1.0",
                executing.getId(), attemptId, executing.getCorrelationId(), executing.getInputHash(),
                "1.0", result, mapper.createArrayNode(), mapper.createArrayNode(), null);
        }).when(client).execute(any(), anyString(), any());
        doAnswer(invocation -> {
            TaskRunWorkerContext context = invocation.getArgument(0);
            String attemptId = invocation.getArgument(1);
            return new ExecutionResponse("1.0", context.taskType().name(), context.taskSchemaVersion(),
                context.taskRunId(), attemptId, context.correlationId(), context.inputHash(),
                "1.0", result, mapper.createArrayNode(), mapper.createArrayNode(), null);
        }).when(client).executeWorker(any(), anyString(), any());
    }

    private byte[] ledgerBundle(TaskRun task, String attemptId, JsonNode result) throws Exception {
        JsonNode input = mapper.readTree(task.getInputSnapshot());
        byte[] a3 = "{\"S1\":\"body\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] resultBytes = mapper.writeValueAsBytes(result);
        byte[] run = "{\"event\":\"complete\"}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("artifactContractVersion", MarketLedgerArtifactService.CONTRACT_VERSION);
        manifest.put("projectId", task.getProject().getId());
        manifest.put("conceptId", input.path("conceptId").asText());
        manifest.put("conceptSnapshotHash", input.path("source").path("selectedConceptHash").asText());
        manifest.put("canonicalInputHash", task.getInputHash());
        manifest.put("marketTaskRunId", task.getId()); manifest.put("taskAttemptId", attemptId);
        manifest.put("asOf", input.path("asOf").asText());
        manifest.put("sourceRunId", input.path("conceptId").asText());
        var files = manifest.putArray("files");
        ledgerFile(files.addObject(), "a3_bodies.json", a3);
        ledgerFile(files.addObject(), "result.json", resultBytes);
        ledgerFile(files.addObject(), "run.jsonl", run);
        manifest.put("manifestHash", sha256(mapper.writeValueAsBytes(manifest)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            ledgerEntry(zip, "a3_bodies.json", a3); ledgerEntry(zip, "result.json", resultBytes);
            ledgerEntry(zip, "run.jsonl", run); ledgerEntry(zip, "manifest.json", mapper.writeValueAsBytes(manifest));
        }
        return bytes.toByteArray();
    }

    private static void ledgerFile(ObjectNode node, String name, byte[] content) {
        node.put("name", name); node.put("sizeBytes", content.length); node.put("sha256", sha256(content));
    }
    private static void ledgerEntry(ZipOutputStream zip, String name, byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(name)); zip.write(content); zip.closeEntry();
    }
    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private JsonNode fixture(String relative) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 5 && root != null; depth++, root = root.getParent()) {
            Path candidate = root.resolve("ai/tests/fixtures/" + relative);
            if (Files.exists(candidate)) {
                ObjectNode node = (ObjectNode) mapper.readTree(Files.readString(candidate));
                node.propertyNames().stream().filter(key -> key.startsWith("_")).toList().forEach(node::remove);
                return node;
            }
        }
        throw new IllegalStateException("Fixture not found: " + relative);
    }

    private record Context(User owner, Project project, String suffix) { }
}
