package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.*;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class InternalAiExecutionClientTests {
    @Test
    void callsOnlyTargetInternalExecutionEndpointAndEchoesIdentity() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> runId = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/ai/executions", exchange -> {
            path.set(exchange.getRequestURI().getPath()); authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String response = """
                {"contractVersion":"1.0","taskType":"IDEA_BRIEF_DERIVATION","taskSchemaVersion":"1.0",
                "taskRunId":"%s","taskAttemptId":"attempt-1","correlationId":"correlation-1",
                "canonicalInputHash":"sha256:%s","resultSchemaVersion":"1.0","result":{"readiness":"APPROPRIATE"},
                "warnings":[],"provenance":[],"usage":null}
                """.formatted(runId.get(), "a".repeat(64));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            AiServerProperties properties = new AiServerProperties("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMinutes(15), Duration.ofMinutes(14), "test-token");
            ObjectMapper mapper = new ObjectMapper();
            InternalAiExecutionClient client = new InternalAiExecutionClient(RestClient.builder().baseUrl(properties.baseUrl()).build(), properties, mapper);
            TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF_DERIVATION_RUN", "subject-1", "{}", "sha256:" + "a".repeat(64), "key", "correlation-1", 3);
            runId.set(run.getId());
            var result = client.execute(run, "attempt-1", LocalDateTime.of(2035, 1, 1, 0, 0));
            assertThat(result.taskRunId()).isEqualTo(run.getId());
            assertThat(path.get()).isEqualTo("/internal/v1/ai/executions");
            assertThat(authorization.get()).isEqualTo("Bearer test-token");
        } finally { server.stop(0); }
    }

    @Test
    void rejectsRequestAboveRawTwoMiBBeforeNetworkCall() {
        AiServerProperties properties = new AiServerProperties("http://127.0.0.1:1", Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(15), Duration.ofMinutes(14), "test-token");
        ObjectMapper mapper = new ObjectMapper();
        InternalAiExecutionClient client = new InternalAiExecutionClient(
            RestClient.builder().baseUrl(properties.baseUrl()).build(), properties, mapper);
        String input = "{\"text\":\"" + "x".repeat(InternalAiExecutionClient.MAX_JSON_BYTES) + "\"}";
        TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF_DERIVATION_RUN", "subject-1",
            input, "sha256:" + "a".repeat(64), "key", "correlation-1", 3);

        assertThatThrownBy(() -> client.execute(run, "attempt-1", LocalDateTime.of(2035, 1, 1, 0, 0)))
            .isInstanceOfSatisfying(ExecutionFailure.class,
                failure -> assertThat(failure.reason()).isEqualTo("REQUEST_BYTES_EXCEEDED"));
    }

    @Test
    void rejectsResponseAboveRawTwoMiBBeforeDeserialization() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/ai/executions", exchange -> {
            byte[] bytes = ("x".repeat(InternalAiExecutionClient.MAX_JSON_BYTES + 1)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiServerProperties properties = new AiServerProperties("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMinutes(15), Duration.ofMinutes(14), "test-token");
            InternalAiExecutionClient client = new InternalAiExecutionClient(
                RestClient.builder().baseUrl(properties.baseUrl()).build(), properties, new ObjectMapper());
            TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF_DERIVATION_RUN", "subject-1",
                "{}", "sha256:" + "a".repeat(64), "key", "correlation-1", 3);

            assertThatThrownBy(() -> client.execute(run, "attempt-1", LocalDateTime.of(2035, 1, 1, 0, 0)))
                .isInstanceOfSatisfying(ExecutionFailure.class,
                    failure -> assertThat(failure.reason()).isEqualTo("RESPONSE_BYTES_EXCEEDED"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsMismatchedResponseIdentity() throws Exception {
        assertResponseIdentityRejected("other-attempt", "sha256:" + "a".repeat(64));
    }

    @Test
    void rejectsMismatchedCanonicalInputHash() throws Exception {
        assertResponseIdentityRejected("attempt-1", "sha256:" + "b".repeat(64));
    }

    @Test
    void rejectsDeadlineErrorWithNonCanonicalRetryability() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/ai/executions", exchange -> {
            byte[] bytes = """
                {"error":{"code":"DEADLINE_EXCEEDED","message":"deadline","correlationId":"correlation-1",
                "taskRunId":"task-run","taskAttemptId":"attempt-1","retryable":false,
                "details":[{"reason":"REQUEST_DEADLINE_EXCEEDED"}]}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(504, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiServerProperties properties = new AiServerProperties("http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMinutes(15), Duration.ofMinutes(14), "test-token");
            InternalAiExecutionClient client = new InternalAiExecutionClient(
                RestClient.builder().baseUrl(properties.baseUrl()).build(), properties, new ObjectMapper());
            TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF_DERIVATION_RUN", "subject-1",
                "{}", "sha256:" + "a".repeat(64), "key", "correlation-1", 3);

            assertThatThrownBy(() -> client.execute(run, "attempt-1", LocalDateTime.of(2035, 1, 1, 0, 0)))
                .isInstanceOfSatisfying(ExecutionFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo("RESULT_SCHEMA_INVALID");
                    assertThat(failure.reason()).isEqualTo("RESULT_DOMAIN_INVARIANT_VIOLATION");
                });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptsProviderResponseSchemaRejectedAsPermanentResultFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/ai/executions", exchange -> {
            byte[] bytes = """
                {"error":{"code":"RESULT_SCHEMA_INVALID","message":"safe internal failure",
                "correlationId":"correlation-1","taskRunId":"task-run","taskAttemptId":"attempt-1",
                "retryable":false,"details":[{"reason":"PROVIDER_RESPONSE_SCHEMA_REJECTED"}]}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(502, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiServerProperties properties = new AiServerProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMinutes(15), Duration.ofMinutes(14), "test-token");
            InternalAiExecutionClient client = new InternalAiExecutionClient(
                RestClient.builder().baseUrl(properties.baseUrl()).build(), properties, new ObjectMapper());
            TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION,
                "IDEA_BRIEF_DERIVATION_RUN", "1", "{}", "sha256:" + "a".repeat(64),
                "key", "correlation-1", 3);

            assertThatThrownBy(() -> client.execute(run, "attempt-1",
                    LocalDateTime.of(2035, 1, 1, 0, 0)))
                .isInstanceOfSatisfying(ExecutionFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo("RESULT_SCHEMA_INVALID");
                    assertThat(failure.reason()).isEqualTo("PROVIDER_RESPONSE_SCHEMA_REJECTED");
                    assertThat(failure.retryable()).isFalse();
                });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptsSafeRetryAfterFromRateLimitFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/ai/executions", exchange -> {
            byte[] bytes = """
                {"error":{"code":"RATE_LIMITED","message":"safe internal failure",
                "correlationId":"correlation-1","taskRunId":"task-run","taskAttemptId":"attempt-1",
                "retryable":true,"details":[{"reason":"DEPENDENCY_RATE_LIMITED","retryAfterMs":7000}]}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiServerProperties properties = new AiServerProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMinutes(15), Duration.ofMinutes(14), "test-token");
            InternalAiExecutionClient client = new InternalAiExecutionClient(
                RestClient.builder().baseUrl(properties.baseUrl()).build(), properties, new ObjectMapper());
            TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION,
                "IDEA_BRIEF_DERIVATION_RUN", "1", "{}", "sha256:" + "a".repeat(64),
                "key", "correlation-1", 3);

            assertThatThrownBy(() -> client.execute(run, "attempt-1",
                    LocalDateTime.of(2035, 1, 1, 0, 0)))
                .isInstanceOfSatisfying(ExecutionFailure.class, failure -> {
                    assertThat(failure.reason()).isEqualTo("DEPENDENCY_RATE_LIMITED");
                    assertThat(failure.retryable()).isTrue();
                    assertThat(failure.retryAfterMillis()).isEqualTo(7_000L);
                });
        } finally {
            server.stop(0);
        }
    }

    private void assertResponseIdentityRejected(String responseAttemptId, String responseHash) throws Exception {
        AtomicReference<String> runId = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/ai/executions", exchange -> {
            String response = """
                {"contractVersion":"1.0","taskType":"IDEA_BRIEF_DERIVATION","taskSchemaVersion":"1.0",
                "taskRunId":"%s","taskAttemptId":"%s","correlationId":"correlation-1",
                "canonicalInputHash":"%s","resultSchemaVersion":"1.0","result":{"readiness":"APPROPRIATE"},
                "warnings":[],"provenance":[],"usage":null}
                """.formatted(runId.get(), responseAttemptId, responseHash);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiServerProperties properties = new AiServerProperties("http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMinutes(15), Duration.ofMinutes(14), "test-token");
            InternalAiExecutionClient client = new InternalAiExecutionClient(
                RestClient.builder().baseUrl(properties.baseUrl()).build(), properties, new ObjectMapper());
            TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF_DERIVATION_RUN", "subject-1",
                "{}", "sha256:" + "a".repeat(64), "key", "correlation-1", 3);
            runId.set(run.getId());

            assertThatThrownBy(() -> client.execute(run, "attempt-1", LocalDateTime.of(2035, 1, 1, 0, 0)))
                .isInstanceOfSatisfying(ExecutionFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo("RESULT_SCHEMA_INVALID");
                    assertThat(failure.reason()).isEqualTo("RESULT_DOMAIN_INVARIANT_VIOLATION");
                });
        } finally {
            server.stop(0);
        }
    }
}
