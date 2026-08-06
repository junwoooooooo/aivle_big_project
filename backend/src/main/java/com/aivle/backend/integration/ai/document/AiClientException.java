package com.aivle.backend.integration.ai.document;

import lombok.Getter;

import java.time.Duration;

@Getter
public class AiClientException extends RuntimeException {
    private final String errorCode;
    private final String safeMessage;
    private final boolean retryable;
    private final Duration retryAfter;

    public AiClientException(
        String errorCode,
        String safeMessage,
        boolean retryable,
        Duration retryAfter,
        Throwable cause
    ) {
        super(safeMessage, cause);
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }
}
