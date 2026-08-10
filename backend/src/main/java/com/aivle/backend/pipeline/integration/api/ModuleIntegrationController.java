package com.aivle.backend.pipeline.integration.api;

import static com.aivle.backend.pipeline.integration.api.IntegrationApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.integration.application.ModuleIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}")
@RequiredArgsConstructor
public class ModuleIntegrationController {
    private final ModuleIntegrationService service;
    private final CurrentUserProvider currentUser;

    @PostMapping("/module-handoffs")
    public ResponseEntity<ApiResponse<HandoffResponse>> handoff(@PathVariable Long projectId,
            @Valid @RequestBody CreateHandoffRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.create(currentUser.currentUserId(), projectId, body), request.getHeader("X-Request-Id")));
    }

    @GetMapping("/module-runs")
    public ApiResponse<ModuleRunListResponse> runs(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.list(currentUser.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/module-runs/{runId}")
    public ApiResponse<ModuleRunResponse> run(@PathVariable Long projectId, @PathVariable String runId, HttpServletRequest request) {
        return ApiResponse.success(service.get(currentUser.currentUserId(), projectId, runId), request.getHeader("X-Request-Id"));
    }
}
