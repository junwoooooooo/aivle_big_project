package com.aivle.backend.integration.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aivle.backend.integration.ai.dto.AiServerHealthResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AiServerHealthClientTests {

    @Test
    void preservesCompatibilityHealthAndUsesTypedResponse() {
        Fixture fixture = fixture("");
        fixture.server.expect(requestTo("http://ai.test/health"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Request-Id", "health-request"))
            .andRespond(withSuccess(
                """
                {
                  "status":"ok",
                  "service":"ai-server",
                  "request_id":"health-request"
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        AiServerHealthResponse response =
            fixture.client.checkHealth("health-request");

        assertEquals("ok", response.status());
        assertEquals("ai-server", response.service());
        assertEquals("health-request", response.requestId());
        fixture.server.verify();
    }

    @Test
    void readyCallPropagatesGeneratedRequestIdAndOptionalApiKey() {
        Fixture fixture = fixture("internal-test-key");
        fixture.server.expect(requestTo("http://ai.test/health/ready"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(request -> {
                String requestId = request.getHeaders()
                    .getFirst("X-Request-Id");
                assertTrue(requestId != null && !requestId.isBlank());
                assertEquals(
                    "internal-test-key",
                    request.getHeaders()
                        .getFirst("X-Internal-Api-Key")
                );
            })
            .andRespond(withSuccess(
                """
                {
                  "status":"ready",
                  "service":"ai-server",
                  "request_id":"generated-by-spring"
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        AiServerHealthResponse response =
            fixture.client.checkReady(null);

        assertEquals("ready", response.status());
        fixture.server.verify();
    }

    @Test
    void blankApiKeyDoesNotAddInternalHeader() {
        AiServerClientSupport support = support("");
        HttpHeaders headers = new HttpHeaders();

        support.addHeaders(headers, "request-id");

        assertEquals("request-id", headers.getFirst("X-Request-Id"));
        assertFalse(headers.containsHeader("X-Internal-Api-Key"));
    }

    @Test
    void malformedJsonAndMissingBodyBecomeSafeInvalidResponses() {
        Fixture malformed = fixture("");
        malformed.server.expect(requestTo("http://ai.test/health"))
            .andRespond(withSuccess(
                "{not-json",
                MediaType.APPLICATION_JSON
            ));

        AiServerException malformedException = assertThrows(
            AiServerException.class,
            () -> malformed.client.checkHealth("malformed-id")
        );
        assertEquals(
            "AI_SERVER_INVALID_RESPONSE",
            malformedException.getErrorCode()
        );
        assertFalse(malformedException.isRetryable());

        Fixture empty = fixture("");
        empty.server.expect(requestTo("http://ai.test/health"))
            .andRespond(withSuccess());
        AiServerException emptyException = assertThrows(
            AiServerException.class,
            () -> empty.client.checkHealth("empty-id")
        );
        assertEquals(
            "AI_SERVER_INVALID_RESPONSE",
            emptyException.getErrorCode()
        );
    }

    private Fixture fixture(String apiKey) {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://ai.test");
        MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
        AiServerHealthClient client = new AiServerHealthClient(
            builder.build(),
            support(apiKey)
        );
        return new Fixture(server, client);
    }

    private AiServerClientSupport support(String apiKey) {
        return new AiServerClientSupport(
            new AiServerProperties(
                "http://ai.test",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                apiKey
            ),
            new ObjectMapper()
        );
    }

    private record Fixture(
        MockRestServiceServer server,
        AiServerHealthClient client
    ) {
    }
}
