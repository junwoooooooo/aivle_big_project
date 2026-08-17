package com.aivle.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    @Test
    void nestedClientAbortIsDetectedThroughWrapperChain() {
        Throwable wrapped = new IllegalStateException("write failed",
            new RuntimeException(new AsyncRequestNotUsableException("response unusable",
                new ClientAbortException("broken pipe"))));
        org.assertj.core.api.Assertions.assertThat(GlobalExceptionHandler.isClientDisconnect(wrapped)).isTrue();
    }

    @Test
    void nestedConnectionResetIOExceptionIsDetected() {
        Throwable wrapped = new IllegalStateException("write failed",
            new IOException("Connection reset by peer"));
        org.assertj.core.api.Assertions.assertThat(GlobalExceptionHandler.isClientDisconnect(wrapped)).isTrue();
    }

    @Test
    void genericHandlerDoesNotWriteJsonAfterCommittedSseDisconnect() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(true);
        when(response.getContentType()).thenReturn("text/event-stream");
        var wrapped = new IllegalStateException("message conversion failed",
            new AsyncRequestNotUsableException("response unusable", new IOException("broken pipe")));
        org.assertj.core.api.Assertions.assertThat(
            handler.handleUnexpected(wrapped, request, response)).isNull();
    }
}
