package com.aivle.backend.pipeline.concept.api;

import static com.aivle.backend.pipeline.concept.api.ConceptFactoryApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
public class ConceptFactoryController {
    private final ConceptFactoryService service;
    private final CurrentUserProvider currentUser;

    @PostMapping("/concept-factory-runs")
    public ResponseEntity<ApiResponse<RunResponse>> create(@PathVariable Long projectId, @Valid @RequestBody CreateRunRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(success(service.create(currentUser.currentUserId(), projectId, body), request));
    }

    @GetMapping("/concept-factory-runs/current")
    public ApiResponse<RunResponse> current(@PathVariable Long projectId, HttpServletRequest request) {
        return success(service.current(currentUser.currentUserId(), projectId), request);
    }

    @GetMapping("/concept-factory-runs/{runId}")
    public ApiResponse<RunResponse> get(@PathVariable Long projectId, @PathVariable String runId, HttpServletRequest request) {
        return success(service.get(currentUser.currentUserId(), projectId, runId), request);
    }

    @GetMapping("/concept-factory-runs/{runId}/slots")
    public ApiResponse<List<SlotResponse>> slots(@PathVariable Long projectId, @PathVariable String runId, HttpServletRequest request) {
        return success(service.slots(currentUser.currentUserId(), projectId, runId), request);
    }

    @PostMapping("/concept-factory-runs/{runId}/retry")
    public ResponseEntity<ApiResponse<RunResponse>> retry(@PathVariable Long projectId, @PathVariable String runId,
            @Valid @RequestBody RetryRunRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(success(
            service.retry(currentUser.currentUserId(), projectId, runId, body.idempotencyKey()), request));
    }

    @GetMapping("/concepts")
    public ApiResponse<ConceptListResponse> concepts(@PathVariable Long projectId, HttpServletRequest request) {
        return success(service.publicConcepts(currentUser.currentUserId(), projectId), request);
    }

    private <T> ApiResponse<T> success(T body, HttpServletRequest request) {
        return ApiResponse.success(body, request.getHeader("X-Request-Id"));
    }
}
