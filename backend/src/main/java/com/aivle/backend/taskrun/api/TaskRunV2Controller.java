package com.aivle.backend.taskrun.api;

import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.service.TaskRunService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/projects/{projectId}/task-runs")
public class TaskRunV2Controller {
    private final TaskRunService service;
    private final CurrentUserProvider users;
    public TaskRunV2Controller(TaskRunService service, CurrentUserProvider users) { this.service = service; this.users = users; }

    @GetMapping("/{taskRunId}")
    public ResponseEntity<Envelope<TaskRunView>> get(@PathVariable Long projectId, @PathVariable String taskRunId, HttpServletRequest request) {
        return response(HttpStatus.OK, view(service.getOwned(users.currentUserId(), projectId, taskRunId)), request);
    }

    @PostMapping("/{taskRunId}/retry")
    public ResponseEntity<Envelope<TaskRunView>> retry(@PathVariable Long projectId, @PathVariable String taskRunId,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
        return response(HttpStatus.ACCEPTED, view(service.retry(users.currentUserId(), projectId, taskRunId, idempotencyKey)), request);
    }

    @PostMapping("/{taskRunId}/cancel")
    public ResponseEntity<Envelope<TaskRunView>> cancel(@PathVariable Long projectId, @PathVariable String taskRunId, HttpServletRequest request) {
        return response(HttpStatus.OK, view(service.cancel(users.currentUserId(), projectId, taskRunId)), request);
    }

    private ResponseEntity<Envelope<TaskRunView>> response(HttpStatus status, TaskRunView data, HttpServletRequest request) {
        String correlation = CorrelationIds.resolve(request); return ResponseEntity.status(status).header("X-Correlation-Id", correlation).body(new Envelope<>(data, new Meta(correlation)));
    }
    private TaskRunView view(TaskRun run) {
        return new TaskRunView(run.getId(), run.getTaskType().name(), new Subject(run.getSubjectType(), run.getSubjectId()),
            run.getState().name(), run.isRetryable(), !run.terminal(), utc(run.getCreatedAt()), utc(run.getStartedAt()), utc(run.getFinishedAt()),
            run.getLastErrorCode() == null ? null : new ErrorSummary(run.getLastErrorCode()),
            run.getFinalResultId() == null ? null : new ResourceReference("TASK_RESULT", run.getFinalResultId()), run.getCorrelationId());
    }
    private String utc(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC).toString(); }
    public record Envelope<T>(T data, Meta meta) {}
    public record Meta(String correlationId) {}
    public record Subject(String type, String id) {}
    public record ResourceReference(String type, String id) {}
    public record ErrorSummary(String code) {}
    public record TaskRunView(String id, String taskType, Subject subject, String state, boolean retryable, boolean cancelable,
                              String createdAt, String startedAt, String finishedAt,
                              ErrorSummary errorSummary, ResourceReference resultResource, String correlationId) {}
}
