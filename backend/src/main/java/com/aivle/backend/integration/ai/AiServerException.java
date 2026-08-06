package com.aivle.backend.integration.ai;

import lombok.Getter;

@Getter
public class AiServerException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;
    private final boolean retryable;
    private final String requestId;
    private final String safeMessage;

    public AiServerException(
        int statusCode,
        String errorCode,
        boolean retryable,
        String requestId,
        String safeMessage,
        Throwable cause
    ) {
        super(safeMessage, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.requestId = requestId;
        this.safeMessage = safeMessage;
    }
}
