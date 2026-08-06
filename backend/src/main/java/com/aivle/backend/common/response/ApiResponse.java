package com.aivle.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error, Meta meta) {
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(true, data, null, Meta.create(requestId));
    }

    public static ApiResponse<Void> failure(String code, String message, List<FieldError> fieldErrors,
                                            boolean retryable, String requestId) {
        return failure(code, message, fieldErrors, retryable, requestId, null);
    }

    public static ApiResponse<Void> failure(String code, String message, List<FieldError> fieldErrors,
                                            boolean retryable, String requestId, LoginAttempt loginAttempt) {
        return new ApiResponse<>(false, null, new ApiError(
            code, message, fieldErrors, retryable,
            loginAttempt == null ? null : loginAttempt.retryAfterSeconds(), loginAttempt
        ), Meta.create(requestId));
    }

    public record ApiError(String code, String message, List<FieldError> fieldErrors, boolean retryable,
                           Long retryAfterSeconds, LoginAttempt loginAttempt) {}
    public record LoginAttempt(String warningLevel, int remainingAttempts, Long retryAfterSeconds) {}
    public record FieldError(String field, String message) {}
    public record Meta(String requestId, Instant timestamp) {
        private static Meta create(String requestId) {
            return new Meta(requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId, Instant.now());
        }
    }
}
