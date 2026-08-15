package com.aivle.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AiServerTransportContractTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiServerClientConfiguration configuration = new AiServerClientConfiguration();

    @Test
    void marketRequestOutlivesTheShortTransportWindow() throws Exception {
        HttpServer server = delayedEchoServer(Duration.ofMillis(350));
        try {
            AiServerProperties properties = properties(server, Duration.ofMillis(100), Duration.ofSeconds(1));
            InternalAiExecutionClient client = client(properties);
            TaskRun run = run(TaskType.MARKET_RESEARCH);

            assertThat(client.execute(run, "attempt-1", LocalDateTime.now().plusMinutes(20)).taskRunId())
                .isEqualTo(run.getId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void techOpsUsesLongTransportWhileShortTaskStillTimesOut() throws Exception {
        HttpServer techOpsServer = delayedEchoServer(Duration.ofMillis(600));
        try {
            AiServerProperties properties = properties(
                techOpsServer, Duration.ofMillis(250), Duration.ofSeconds(2));
            InternalAiExecutionClient client = client(properties);
            TaskRun techOps = run(TaskType.TECH_OPS_ADVISORY);

            assertThat(client.execute(
                techOps, "attempt-1", LocalDateTime.now().plusMinutes(6)
            ).taskRunId()).isEqualTo(techOps.getId());
        } finally {
            techOpsServer.stop(0);
        }

        HttpServer shortTaskServer = delayedEchoServer(Duration.ofMillis(600));
        try {
            AiServerProperties properties = properties(
                shortTaskServer, Duration.ofMillis(250), Duration.ofSeconds(2));
            InternalAiExecutionClient client = client(properties);

            assertThatThrownBy(() -> client.execute(
                run(TaskType.IDEA_BRIEF_DERIVATION),
                "attempt-1",
                LocalDateTime.now().plusMinutes(1)
            )).isInstanceOfSatisfying(ExecutionFailure.class, failure -> {
                assertThat(failure.code()).isEqualTo("DEADLINE_EXCEEDED");
                assertThat(failure.reason()).isEqualTo("REQUEST_DEADLINE_EXCEEDED");
            });
        } finally {
            shortTaskServer.stop(0);
        }
    }

    @Test
    void connectionFailureIsDependencyUnavailableNotDeadline() {
        AiServerProperties properties = new AiServerProperties(
            "http://127.0.0.1:1", Duration.ofMillis(100), Duration.ofMillis(100),
            Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
            Duration.ofSeconds(1), "test-token");
        InternalAiExecutionClient client = client(properties);
        TaskRun run = run(TaskType.IDEA_BRIEF_DERIVATION);

        assertThatThrownBy(() -> client.execute(run, "attempt-1", LocalDateTime.now().plusMinutes(1)))
            .isInstanceOfSatisfying(ExecutionFailure.class, failure -> {
                assertThat(failure.code()).isEqualTo("DEPENDENCY_UNAVAILABLE");
                assertThat(failure.reason()).isEqualTo("MODEL_DEPENDENCY_UNAVAILABLE");
            });
    }

    private InternalAiExecutionClient client(AiServerProperties properties) {
        return new InternalAiExecutionClient(
            configuration.createRestClient(properties, properties.readTimeout()),
            configuration.createRestClient(properties, properties.longReadTimeout()),
            configuration.createRestClient(properties, properties.marketResearchReadTimeout()),
            configuration.createRestClient(properties, properties.conceptPortfolioReadTimeout()),
            configuration.createRestClient(properties, properties.twinSurveyReadTimeout()),
            properties,
            mapper
        );
    }

    private AiServerProperties properties(HttpServer server, Duration shortTimeout, Duration marketTimeout) {
        return new AiServerProperties(
            "http://127.0.0.1:" + server.getAddress().getPort(),
            Duration.ofSeconds(1), shortTimeout, Duration.ofSeconds(1), marketTimeout,
            Duration.ofSeconds(1), Duration.ofSeconds(1), "test-token"
        );
    }

    private TaskRun run(TaskType type) {
        return TaskRun.create(null, type, type.name() + "_SUBJECT", "subject-1", "{}",
            "sha256:" + "a".repeat(64), "key", "correlation-1", 3);
    }

    private HttpServer delayedEchoServer(Duration delay) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/ai/executions", exchange -> {
            try {
                JsonNode request = mapper.readTree(exchange.getRequestBody().readAllBytes());
                Thread.sleep(delay.toMillis());
                String response = """
                    {"contractVersion":"%s","taskType":"%s","taskSchemaVersion":"%s",
                    "taskRunId":"%s","taskAttemptId":"%s","correlationId":"%s",
                    "canonicalInputHash":"%s","resultSchemaVersion":"1.0","result":{},
                    "warnings":[],"provenance":[],"usage":null}
                    """.formatted(
                    request.get("contractVersion").asText(), request.get("taskType").asText(),
                    request.get("taskSchemaVersion").asText(), request.get("taskRunId").asText(),
                    request.get("taskAttemptId").asText(), request.get("correlationId").asText(),
                    request.get("canonicalInputHash").asText());
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }
}
