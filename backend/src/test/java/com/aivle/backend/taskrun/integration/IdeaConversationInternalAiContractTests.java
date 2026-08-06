package com.aivle.backend.taskrun.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class IdeaConversationInternalAiContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesTheCanonicalSharedRequestFixture() throws Exception {
        JsonNode fixture = fixture("idea-conversation-turn-v1.request.json");
        InternalAiExecutionClient client = client("http://127.0.0.1:1");

        JsonNode actual = client.requestPayload(context(fixture), "attempt-conversation-1",
            LocalDateTime.of(2035, 1, 1, 0, 0));

        assertThat(actual).isEqualTo(fixture);
        assertThat(actual.path("taskType").asText()).isEqualTo("IDEA_CONVERSATION_TURN");
        assertThat(actual.path("input").path("currentBrief").isNull()).isTrue();
        assertThat(actual.path("input").path("attachments").isEmpty()).isTrue();
    }

    @Test
    void acceptsTheSharedAiResponseFixture() throws Exception {
        JsonNode request = fixture("idea-conversation-turn-v1.request.json");
        byte[] response = Files.readAllBytes(fixturePath("idea-conversation-turn-v1.response.json"));
        AtomicReference<JsonNode> captured = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            captured.set(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        });
        try {
            var result = client("http://127.0.0.1:" + server.getAddress().getPort())
                .executeWorker(context(request), "attempt-conversation-1",
                    LocalDateTime.of(2035, 1, 1, 0, 0));

            assertThat(result.taskType()).isEqualTo("IDEA_CONVERSATION_TURN");
            assertThat(result.result().path("readiness").asText()).isEqualTo("NEEDS_INPUT");
            assertThat(captured.get()).isEqualTo(request);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsRequestValidationFieldsWithoutRetryingOrExposingValues() throws Exception {
        byte[] response = """
            {"error":{"code":"INVALID_REQUEST","message":"safe","correlationId":"correlation-conversation-1",
            "taskRunId":"run-conversation-1","taskAttemptId":"attempt-conversation-1","retryable":false,
            "details":[{"reason":"FIELD_CONSTRAINT_VIOLATION","fields":[
            {"path":"input.currentBrief","expectedType":"object","category":"missing"}]}]}}
            """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, response.length);
            exchange.getResponseBody().write(response);
        });
        try {
            JsonNode request = fixture("idea-conversation-turn-v1.request.json");
            assertThatThrownBy(() -> client("http://127.0.0.1:" + server.getAddress().getPort())
                .executeWorker(context(request), "attempt-conversation-1",
                    LocalDateTime.of(2035, 1, 1, 0, 0)))
                .isInstanceOfSatisfying(ExecutionFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo("INVALID_REQUEST");
                    assertThat(failure.retryable()).isFalse();
                    assertThat(failure.validationFields()).containsExactly(
                        new InternalAiExecutionClient.ValidationIssue(
                            "input.currentBrief", "object", "missing"));
                    assertThat(failure.getMessage()).doesNotContain("서울의 재활용");
                });
        } finally {
            server.stop(0);
        }
    }

    private TaskRunWorkerContext context(JsonNode fixture) {
        return new TaskRunWorkerContext(
            fixture.path("taskRunId").asText(), 101L, 201L, TaskType.IDEA_CONVERSATION_TURN,
            "IDEA_CONVERSATION_MESSAGE", "403", fixture.path("input").toString(),
            fixture.path("canonicalInputHash").asText(), "idea-conversation-turn:403",
            fixture.path("correlationId").asText(), fixture.path("contractVersion").asText(),
            fixture.path("taskSchemaVersion").asText(), fixture.path("locale").asText(), 1, 3);
    }

    private InternalAiExecutionClient client(String baseUrl) {
        AiServerProperties properties = new AiServerProperties(
            baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2), "test-token");
        return new InternalAiExecutionClient(
            RestClient.builder().baseUrl(baseUrl).build(), properties, mapper);
    }

    private HttpServer server(Handler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/ai/executions", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private JsonNode fixture(String name) throws Exception {
        return mapper.readTree(Files.readAllBytes(fixturePath(name)));
    }

    private Path fixturePath(String name) {
        Path repository = Path.of("..", "contracts", "internal-ai", name);
        return Files.exists(repository) ? repository : Path.of("contracts", "internal-ai", name);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception;
    }
}
