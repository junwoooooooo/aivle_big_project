package com.aivle.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class GlobalExceptionHandlerSseTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void clientAbortDoesNotAttemptToCreateAnApiResponse() {
        assertThatCode(() -> handler.handleDisconnectedClient(
            new ClientAbortException("broken pipe"), request)).doesNotThrowAnyException();
    }

    @Test
    void committedAsyncResponseFailureDoesNotAttemptToCreateAnApiResponse() {
        assertThatCode(() -> handler.handleDisconnectedClient(
            new AsyncRequestNotUsableException("response is committed"), request))
            .doesNotThrowAnyException();
    }
}
