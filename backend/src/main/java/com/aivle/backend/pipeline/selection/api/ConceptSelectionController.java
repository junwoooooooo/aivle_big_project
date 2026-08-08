package com.aivle.backend.pipeline.selection.api;

import static com.aivle.backend.pipeline.selection.api.SelectionApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.selection.application.ConceptSelectionService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/concept-selections")
@RequiredArgsConstructor
public class ConceptSelectionController {
    private final ConceptSelectionService service;
    private final CurrentUserProvider currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<SelectionResponse>> select(@PathVariable Long projectId,
            @Valid @RequestBody CreateSelectionRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.select(currentUser.currentUserId(), projectId, body), request.getHeader("X-Request-Id")));
    }

    @GetMapping("/current")
    public ApiResponse<SelectionResponse> current(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/current/hypotheses/{hypothesisType}/actions")
    public ResponseEntity<ApiResponse<HypothesisActionResponse>> decide(@PathVariable Long projectId,
            @PathVariable String hypothesisType, @Valid @RequestBody HypothesisActionRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        HypothesisActionResponse result = service.decide(currentUser.currentUserId(), projectId,
            hypothesisType, body, idempotencyKey, request.getHeader("X-Request-Id"));
        HttpStatus status = result.taskRunId() == null ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.success(result, request.getHeader("X-Request-Id")));
    }
}
