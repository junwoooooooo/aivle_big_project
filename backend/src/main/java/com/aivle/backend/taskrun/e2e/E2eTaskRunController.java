package com.aivle.backend.taskrun.e2e;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("e2e")
@RequestMapping("/internal/e2e/projects/{projectId}/task-runs")
public class E2eTaskRunController {
    private final E2eTaskRunService service;
    private final CurrentUserProvider users;

    public E2eTaskRunController(E2eTaskRunService service, CurrentUserProvider users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<E2eTaskRunService.StartResult>> start(
            @PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody StartRequest body,
            HttpServletRequest request) {
        var result = service.start(users.currentUserId(), projectId, body.scenario(),
            idempotencyKey, correlationId(request));
        return ResponseEntity.status(result.createdNew() ? HttpStatus.ACCEPTED : HttpStatus.OK)
            .body(ApiResponse.success(result, request.getHeader("X-Request-Id")));
    }

    @GetMapping("/{taskRunId}/result")
    public ApiResponse<JsonNode> result(@PathVariable Long projectId,
            @PathVariable String taskRunId, HttpServletRequest request) {
        return ApiResponse.success(service.result(users.currentUserId(), projectId, taskRunId),
            request.getHeader("X-Request-Id"));
    }

    private String correlationId(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-Id");
        return value == null || value.isBlank()
            ? request.getHeader("X-Request-Id") : value;
    }

    public record StartRequest(E2eTaskRunService.Scenario scenario) {}
}
