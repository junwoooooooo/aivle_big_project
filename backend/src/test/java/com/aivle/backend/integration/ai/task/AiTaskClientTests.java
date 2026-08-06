package com.aivle.backend.integration.ai.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aivle.backend.integration.ai.AiServerClientSupport;
import com.aivle.backend.integration.ai.AiServerException;
import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.integration.ai.task.dto.AiTaskRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AiTaskClientTests {

    private MockRestServiceServer server;
    private AiTaskClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://ai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiTaskClient(
            builder.build(),
            new AiServerClientSupport(
                new AiServerProperties(
                    "http://ai.test",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1),
                    ""
                ),
                objectMapper
            )
        );
    }

    @Test
    void sendsTypedEnvelopeAndPreservesRequestAndTaskIds() {
        server.expect(requestTo("http://ai.test/internal/v1/tasks"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Request-Id", "request-1"))
            .andExpect(request -> {
                String body = ((MockClientHttpRequest) request)
                    .getBodyAsString(StandardCharsets.UTF_8);
                assertThat(body).contains(
                    "\"request_id\":\"request-1\"",
                    "\"task_id\":\"41\"",
                    "\"task_type\":\"SYSTEM_SMOKE_TEST\"",
                    "\"schema_version\":\"1.0\""
                );
            })
            .andRespond(withSuccess(
                successBody(),
                MediaType.APPLICATION_JSON
            ));

        var response = client.execute(request());

        assertThat(response.requestId()).isEqualTo("request-1");
        assertThat(response.taskId()).isEqualTo("41");
        assertThat(response.result().get("ok").asBoolean())
            .isTrue();
        assertThat(response.execution().handler())
            .isEqualTo("system-smoke");
        server.verify();
    }

    @Test
    void convertsTask4xxAnd5xxWithoutRetrying() {
        server.expect(requestTo("http://ai.test/internal/v1/tasks"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody(
                    "UNSUPPORTED_SCHEMA_VERSION",
                    false
                )));
        server.expect(requestTo("http://ai.test/internal/v1/tasks"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody(
                    "AI_SERVER_INTERNAL_ERROR",
                    true
                )));

        assertThatThrownBy(() -> client.execute(request()))
            .isInstanceOfSatisfying(
                AiServerException.class,
                exception -> {
                    assertThat(exception.getStatusCode())
                        .isEqualTo(422);
                    assertThat(exception.getErrorCode())
                        .isEqualTo(
                            "UNSUPPORTED_SCHEMA_VERSION"
                        );
                    assertThat(exception.isRetryable()).isFalse();
                }
            );

        assertThatThrownBy(() -> client.execute(request()))
            .isInstanceOfSatisfying(
                AiServerException.class,
                exception -> {
                    assertThat(exception.getStatusCode())
                        .isEqualTo(500);
                    assertThat(exception.isRetryable()).isTrue();
                    assertThat(exception.getSafeMessage())
                        .isEqualTo(
                            "AI 서버에서 요청을 처리하지 못했습니다."
                        );
                }
            );
        server.verify();
    }

    @Test
    void sendsAndReceivesTypedArtifactContract() {
        server.expect(requestTo("http://ai.test/internal/v1/tasks"))
            .andExpect(request -> {
                String body = ((MockClientHttpRequest) request)
                    .getBodyAsString(StandardCharsets.UTF_8);
                assertThat(body).contains(
                    "\"artifacts\":[",
                    "\"download_url\":\"http://minio/source\"",
                    "\"output_targets\":[",
                    "\"upload_url\":\"http://minio/result\""
                );
            })
            .andRespond(withSuccess(
                artifactSuccessBody(),
                MediaType.APPLICATION_JSON
            ));

        var response = client.execute(artifactRequest());

        assertThat(response.artifacts()).singleElement()
            .satisfies(artifact -> {
                assertThat(artifact.objectKey())
                    .isEqualTo("ai-artifacts/result.json");
                assertThat(artifact.checksum())
                    .startsWith("sha256:");
            });
        server.verify();
    }

    private AiTaskRequest request() {
        return new AiTaskRequest(
            "request-1",
            "41",
            AiTaskType.SYSTEM_SMOKE_TEST,
            "1.0",
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode()
        );
    }

    private AiTaskRequest artifactRequest() {
        return new AiTaskRequest(
            "request-1",
            "41",
            AiTaskType.SYSTEM_ARTIFACT_SMOKE_TEST,
            "1.0",
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            List.of(new AiTaskRequest.ArtifactInput(
                "source-1",
                "SOURCE",
                "ai-artifacts/source.json",
                "http://minio/source",
                "application/json",
                2,
                "sha256:" + "0".repeat(64)
            )),
            List.of(new AiTaskRequest.OutputTarget(
                "RESULT",
                "ai-artifacts/result.json",
                "http://minio/result",
                "application/json"
            ))
        );
    }

    private String successBody() {
        return """
            {
              "request_id":"request-1",
              "task_id":"41",
              "task_type":"SYSTEM_SMOKE_TEST",
              "status":"SUCCEEDED",
              "schema_version":"1.0",
              "result":{"ok":true},
              "warnings":[],
              "execution":{
                "handler":"system-smoke",
                "handler_version":"1.0"
              },
              "error":null
            }
            """;
    }

    private String errorBody(
        String code,
        boolean retryable
    ) {
        return """
            {
              "request_id":"request-1",
              "error":{
                "code":"%s",
                "message":"remote private detail",
                "retryable":%s
              }
            }
            """.formatted(code, retryable);
    }

    private String artifactSuccessBody() {
        return """
            {
              "request_id":"request-1",
              "task_id":"41",
              "task_type":"SYSTEM_ARTIFACT_SMOKE_TEST",
              "status":"SUCCEEDED",
              "schema_version":"1.0",
              "result":{"ok":true},
              "warnings":[],
              "execution":{
                "handler":"system-artifact-smoke",
                "handler_version":"1.0"
              },
              "error":null,
              "artifacts":[{
                "role":"RESULT",
                "object_key":"ai-artifacts/result.json",
                "content_type":"application/json",
                "size":2,
                "checksum":"sha256:%s"
              }]
            }
            """.formatted("0".repeat(64));
    }
}
