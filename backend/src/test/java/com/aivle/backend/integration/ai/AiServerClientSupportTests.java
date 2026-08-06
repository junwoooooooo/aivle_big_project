package com.aivle.backend.integration.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

class AiServerClientSupportTests {

    @Test
    void classifiesNestedGenericReadTimeoutAsRetryableTimeout() {
        AiServerClientSupport support = new AiServerClientSupport(
            new AiServerProperties(
                "http://127.0.0.1:8000",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                ""
            ),
            new ObjectMapper()
        );

        AiServerException exception = assertThrows(
            AiServerException.class,
            () -> support.execute("request-id", () -> {
                throw new RestClientException(
                    "I/O failure",
                    new SocketTimeoutException("Read timed out")
                );
            })
        );

        assertEquals("AI_SERVER_TIMEOUT", exception.getErrorCode());
        assertEquals("request-id", exception.getRequestId());
        assertTrue(exception.isRetryable());
    }
}
