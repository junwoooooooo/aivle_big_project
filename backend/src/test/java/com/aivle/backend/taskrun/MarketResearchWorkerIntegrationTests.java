package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.aivle.backend.journey.MarketResearchWorker;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskResultValidationState;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * <b>조용한 폐기를 잡는 검사.</b>
 *
 * <p>{@code MarketResearchWorker} 의 결과 검증이 없으면
 * 결과가 {@code RESULT_DOMAIN_INVARIANT_VIOLATION} 으로 버려진다 —
 * <b>컴파일도 안 깨지고 다른 테스트도 안 깨진다.</b> AI 비용만 쓰고 조용히 사라진다.
 *
 * <p>그래서 「채택됐는가」를 직접 본다. 스텁 AI 가 <b>AI 쪽과 같은 골든 픽스처</b>를 돌려준다.
 */
// ⚠ **DB 를 따로 쓴다 — 그러려면 URL 을 따로 줘야 한다.**
//    `TaskRunServiceIntegrationTests` 는 `results.findAll()` 로 **전역 조회**를 하므로
//    이 테스트가 만든 TaskResult 3건이 그대로 남의 단언을 깬다(기대 1 · 실제 4).
//    ⚠ 옛 주석은 「속성을 다르게 주면 컨텍스트가 갈리고 그래서 DB 도 갈린다」고 적혀 있었는데
//      **틀렸다**: `application-test.yaml` 의 URL(`jdbc:h2:mem:aivle-test`)이 하나뿐이라
//      컨텍스트가 갈려도 **같은 인메모리 DB** 를 본다. 실측(판 ㉝ 세션 3): 같은 코드가
//      한 번은 통과하고 한 번은 깨졌다 — 격리가 아니라 **실행 순서 운**이었다.
//      이름 있는 DB 를 따로 주는 것이 격리의 유일한 실물 근거다.
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:market-research-worker"
        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@ActiveProfiles("test")
class MarketResearchWorkerIntegrationTests {

    @Autowired TaskRunService service;
    @Autowired MarketResearchWorker worker;
    @Autowired CanonicalInputHasher hasher;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ObjectMapper mapper;
    @Autowired TaskResultRepository results;
    @MockitoBean InternalAiExecutionClient client;

    private JsonNode fixture(String name) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 4; depth++) {
            Path candidate = root.resolve("ai/tests/fixtures/market_research/" + name);
            if (Files.exists(candidate)) {
                ObjectNode node = (ObjectNode) mapper.readTree(Files.readString(candidate));
                node.propertyNames().stream().filter(key -> key.startsWith("_")).toList()
                    .forEach(node::remove);
                return node;
            }
            root = root.getParent();
        }
        throw new IllegalStateException("골든 픽스처를 찾지 못했다: " + name);
    }

    private TaskRun queue(User owner, Project project, String suffix) {
        String input = "{}";
        String hash = hasher.hash(TaskType.MARKET_RESEARCH, "1.0", "ko-KR", input);
        return service.create(owner.getId(), project.getId(), TaskType.MARKET_RESEARCH,
            "MARKET_RESEARCH_FULL", "concept-" + suffix, input, hash,
            "key-" + suffix, "correlation-" + suffix, 1);
    }

    private void stub(JsonNode result) {
        when(client.execute(any(), anyString(), any())).thenAnswer(invocation -> {
            TaskRun executing = invocation.getArgument(0);
            String attemptId = invocation.getArgument(1);
            return new ExecutionResponse("1.0", executing.getTaskType().name(), "1.0",
                executing.getId(), attemptId, executing.getCorrelationId(),
                executing.getInputHash(), "1.0", result,
                mapper.createArrayNode(), mapper.createArrayNode(), null);
        });
    }

    @Test
    @DisplayName("⭐ 골든 픽스처가 채택된다 — 분기가 없으면 여기서 REJECTED 가 나온다")
    void adoptsGoldenFixture() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create("mr-" + suffix + "@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "market research", null, null));
        TaskRun run = queue(owner, project, suffix);
        stub(fixture("full.json"));

        assertThat(worker.processOne()).isTrue();

        TaskRun completed = service.getOwned(owner.getId(), project.getId(), run.getId());
        assertThat(completed.getState()).isEqualTo(TaskRunState.SUCCEEDED);
        assertThat(completed.getFinalResultId()).isNotNull();
        assertThat(results.findById(completed.getFinalResultId()).orElseThrow().getValidationState())
            .isEqualTo(TaskResultValidationState.ADOPTED);
    }

    @Test
    @DisplayName("BM 픽스처도 채택된다 — 한 TaskType 의 두 모드가 같은 봉투를 쓴다")
    void adoptsBmFixture() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create("mrbm-" + suffix + "@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "bm canvas", null, null));
        TaskRun run = queue(owner, project, suffix);
        stub(fixture("bm.json"));

        assertThat(worker.processOne()).isTrue();
        assertThat(service.getOwned(owner.getId(), project.getId(), run.getId()).getState())
            .isEqualTo(TaskRunState.SUCCEEDED);
    }

    @Test
    @DisplayName("경계가 빠진 결과는 채택되지 않는다 — DB 까지 못 들어온다")
    void rejectsResultWithDroppedCaveat() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create("mrc-" + suffix + "@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "caveat drop", null, null));
        TaskRun run = queue(owner, project, suffix);

        ObjectNode broken = (ObjectNode) fixture("bm.json");
        ((tools.jackson.databind.node.ArrayNode)
            broken.get("canvas").get("cells").get(0).get("caveats")).removeAll();
        stub(broken);

        assertThat(worker.processOne()).isTrue();
        TaskRun failed = service.getOwned(owner.getId(), project.getId(), run.getId());
        assertThat(failed.getState()).isEqualTo(TaskRunState.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("AI_RESULT_INVALID");
    }
}
