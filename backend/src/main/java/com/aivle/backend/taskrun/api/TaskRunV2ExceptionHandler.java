package com.aivle.backend.taskrun.api;

import com.aivle.backend.taskrun.service.TaskRunFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@RestControllerAdvice(basePackages = "com.aivle.backend.taskrun")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TaskRunV2ExceptionHandler {
    @ExceptionHandler(TaskRunFailure.class)
    ResponseEntity<ErrorEnvelope> task(TaskRunFailure failure, HttpServletRequest request) {
        String correlation = CorrelationIds.resolve(request);
        return ResponseEntity.status(failure.getStatus()).header("X-Correlation-Id", correlation)
            .body(new ErrorEnvelope(new ErrorBody(failure.getCode(), "Command cannot be executed in the current state.", correlation, null,
                List.of(new Detail(null, failure.getReason(), "TASK_RUN", null)))));
    }
    @ExceptionHandler({MissingRequestHeaderException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorEnvelope> validation(Exception ignored, HttpServletRequest request) {
        String correlation = CorrelationIds.resolve(request);
        return ResponseEntity.badRequest().header("X-Correlation-Id", correlation)
            .body(new ErrorEnvelope(new ErrorBody("VALIDATION_ERROR", "Request validation failed.", correlation, null,
                List.of(new Detail("Idempotency-Key", "REQUIRED", null, null)))));
    }
    record ErrorEnvelope(ErrorBody error) {}
    record ErrorBody(String code, String message, String correlationId, String taskRunId, List<Detail> details) {}
    record Detail(String field, String reason, String resourceType, String resourceId) {}
}
