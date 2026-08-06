package com.aivle.backend.job.runner;

import lombok.Getter;

import java.time.Duration;

@Getter
public class JobProcessingException extends RuntimeException {
    private final String errorCode;
    private final String safeMessage;
    private final boolean retryable;
    private final Duration retryAfter;

    public JobProcessingException(
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

    public static JobProcessingException nonRetryable(
        String errorCode,
        String safeMessage,
        Throwable cause
    ) {
        return new JobProcessingException(errorCode, safeMessage, false, null, cause);
    }

    public static JobProcessingException retryable(
        String errorCode,
        String safeMessage,
        Duration retryAfter,
        Throwable cause
    ) {
        return new JobProcessingException(errorCode, safeMessage, true, retryAfter, cause);
    }
}
