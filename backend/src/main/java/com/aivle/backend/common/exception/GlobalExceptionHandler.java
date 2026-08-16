package com.aivle.backend.common.exception;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.web.RequestIds;
import com.aivle.backend.auth.LoginRateLimitExceededException;
import com.aivle.backend.auth.LoginCredentialsFailedException;
import com.aivle.backend.auth.LoginAttemptRateLimiter.LoginAttemptStatus;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.dao.OptimisticLockingFailureException;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})
    public void handleDisconnectedClient(Exception exception, HttpServletRequest request) {
        log.debug("Async client disconnected, requestId={}", requestId(request));
    }

    @ExceptionHandler(LoginRateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoginRateLimit(
        LoginRateLimitExceededException exception,
        HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.LOGIN_RATE_LIMITED;
        return ResponseEntity.status(code.getHttpStatus())
            .header("Retry-After", Long.toString(exception.getRetryAfterSeconds()))
            .body(ApiResponse.failure(
                code.name(), code.getMessage(), List.of(), false, requestId(request),
                loginAttempt(exception.getStatus())
            ));
    }

    @ExceptionHandler(LoginCredentialsFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoginCredentials(
        LoginCredentialsFailedException exception,
        HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.INVALID_CREDENTIALS;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(
            code.name(), code.getMessage(), List.of(), false, requestId(request),
            loginAttempt(exception.getStatus())
        ));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
        OptimisticLockingFailureException exception,
        HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.RESOURCE_VERSION_CONFLICT;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(
            code.name(),
            code.getMessage(),
            List.of(),
            code.isRetryable(),
            requestId(request)
        ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception, HttpServletRequest request) {
        ErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(
                code.name(), exception.getSafeMessage(), exception.getFieldErrors(),
                code.isRetryable(), requestId(request)));
    }

    @ExceptionHandler(TaskRunFailure.class)
    public ResponseEntity<ApiResponse<Void>> handleTaskRunFailure(
        TaskRunFailure exception,
        HttpServletRequest request
    ) {
        String requestId = requestId(request);
        return ResponseEntity.status(exception.getStatus())
            .header(RequestIds.HEADER, requestId)
            .body(ApiResponse.failure(
                exception.getCode(),
                "Task request could not be accepted.",
                List.of(new ApiResponse.FieldError("taskRun", exception.getReason())),
                exception.isRetryable(),
                requestId
            ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception,
                                                               HttpServletRequest request) {
        List<ApiResponse.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError).toList();
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(
                code.name(), code.getMessage(), errors, false, requestId(request)));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(
        MissingServletRequestPartException exception,
        HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.FILE_REQUIRED;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(
            code.name(), code.getMessage(), List.of(), false, requestId(request)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(
        MaxUploadSizeExceededException exception,
        HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.FILE_TOO_LARGE;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(
            code.name(), code.getMessage(), List.of(), false, requestId(request)));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
        HttpMediaTypeNotSupportedException exception,
        HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.FILE_TYPE_UNSUPPORTED;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(
            code.name(), code.getMessage(), List.of(), false, requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure, requestId={}", requestId(request), exception);
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(
                code.name(), code.getMessage(), List.of(), true, requestId(request)));
    }

    private ApiResponse.FieldError toFieldError(FieldError error) {
        return new ApiResponse.FieldError(error.getField(), error.getDefaultMessage());
    }

    private String requestId(HttpServletRequest request) {
        return RequestIds.resolve(request);
    }

    private ApiResponse.LoginAttempt loginAttempt(LoginAttemptStatus status) {
        return new ApiResponse.LoginAttempt(
            status.warningLevel().name(), status.remainingAttempts(),
            status.limited() ? status.retryAfterSeconds() : null
        );
    }
}
