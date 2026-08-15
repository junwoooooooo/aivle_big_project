package com.aivle.backend.pipeline.conceptportfolio.api;

import static com.aivle.backend.pipeline.conceptportfolio.api.ConceptPortfolioApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioService;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioContinuationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/concept-portfolio-runs")
@RequiredArgsConstructor
public class ConceptPortfolioController {
    private final ConceptPortfolioService service;
    private final ConceptPortfolioContinuationService continuationService;
    private final CurrentUserProvider currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<RunResponse>> create(@PathVariable Long projectId,
            @Valid @RequestBody CreateRunRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.create(currentUser.currentUserId(), projectId, body), requestId(request)));
    }

    @GetMapping("/current")
    public ApiResponse<RunResponse> current(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), requestId(request));
    }

    @GetMapping("/{runId}")
    public ApiResponse<RunResponse> get(@PathVariable Long projectId, @PathVariable String runId,
            HttpServletRequest request) {
        return ApiResponse.success(service.get(currentUser.currentUserId(), projectId, runId), requestId(request));
    }

    @GetMapping("/{runId}/concepts")
    public ApiResponse<List<ConceptResponse>> concepts(@PathVariable Long projectId,
            @PathVariable String runId, HttpServletRequest request) {
        return ApiResponse.success(service.concepts(currentUser.currentUserId(), projectId, runId), requestId(request));
    }

    @GetMapping("/{runId}/input-requests")
    public ApiResponse<List<InputRequestResponse>> inputRequests(@PathVariable Long projectId,
            @PathVariable String runId, HttpServletRequest request) {
        return ApiResponse.success(continuationService.list(currentUser.currentUserId(), projectId, runId),
            requestId(request));
    }

    @PostMapping("/{runId}/input-requests/{inputRequestId}/responses")
    public ResponseEntity<ApiResponse<ContinuationAcceptedResponse>> respond(
            @PathVariable Long projectId, @PathVariable String runId,
            @PathVariable String inputRequestId, @Valid @RequestBody SubmitInputResponseRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            continuationService.submit(currentUser.currentUserId(), projectId, runId, inputRequestId, body),
            requestId(request)));
    }

    @PostMapping("/{runId}/input-requests/{inputRequestId}/retry")
    public ResponseEntity<ApiResponse<ContinuationAcceptedResponse>> retry(
            @PathVariable Long projectId, @PathVariable String runId,
            @PathVariable String inputRequestId, @Valid @RequestBody RetryContinuationRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            continuationService.retry(currentUser.currentUserId(), projectId, runId, inputRequestId, body),
            requestId(request)));
    }

    private String requestId(HttpServletRequest request) { return request.getHeader("X-Request-Id"); }
}
