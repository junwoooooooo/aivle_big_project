package com.aivle.backend.taskrun.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TaskRunFailure extends RuntimeException {
    private final String code;
    private final String reason;
    private final HttpStatus status;
    private final boolean retryable;

    public TaskRunFailure(String code, String reason, HttpStatus status, boolean retryable) {
        super(code); this.code = code; this.reason = reason; this.status = status; this.retryable = retryable;
    }
}
