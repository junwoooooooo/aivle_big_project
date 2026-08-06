package com.aivle.backend.journey.conceptcore;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/projects/{projectId}")
@RequiredArgsConstructor
public class ConceptExplorationController {
    private final ConceptExplorationApplicationService service;
    private final CurrentUserProvider users;

    @PostMapping("/concept-explorations")
    public ResponseEntity<ApiResponse<ConceptExplorationApplicationService.StartView>> start(
            @PathVariable Long projectId, @Valid @RequestBody StartRequest body,
            HttpServletRequest request) {
        var value = service.start(users.currentUserId(), projectId,
            body.confirmedBriefVersionId(), body.regulatoryBoundaryVersionId());
        return ResponseEntity.status(value.jobId() == null ? HttpStatus.CONFLICT : HttpStatus.ACCEPTED)
            .body(ApiResponse.success(value, request.getHeader("X-Request-Id")));
    }

    @GetMapping("/concept-explorations/current")
    public ApiResponse<ConceptExplorationApplicationService.CurrentView> current(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(users.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }

    @PostMapping("/concept-explorations/{batchId}/retry")
    public ResponseEntity<ApiResponse<ConceptExplorationApplicationService.StartView>> retry(
            @PathVariable Long projectId, @PathVariable Long batchId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest request) {
        var value = service.retry(users.currentUserId(), projectId, batchId, idempotencyKey);
        return ResponseEntity.accepted()
            .body(ApiResponse.success(value, request.getHeader("X-Request-Id")));
    }

    @GetMapping("/concept-explorations/{batchId}")
    public ApiResponse<ConceptExplorationApplicationService.BatchDetail> batch(
            @PathVariable Long projectId, @PathVariable Long batchId, HttpServletRequest request) {
        return ApiResponse.success(service.batch(users.currentUserId(), projectId, batchId),
            request.getHeader("X-Request-Id"));
    }

    @GetMapping("/concept-explorations/{batchId}/slots")
    public ApiResponse<List<ConceptExplorationApplicationService.SlotView>> slots(
            @PathVariable Long projectId, @PathVariable Long batchId, HttpServletRequest request) {
        return ApiResponse.success(service.slotViews(users.currentUserId(), projectId, batchId),
            request.getHeader("X-Request-Id"));
    }

    @GetMapping(value = "/concepts", params = "contract=concept-core-v1")
    public ApiResponse<List<ConceptExplorationApplicationService.ConceptView>> concepts(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.concepts(users.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }

    public record StartRequest(@NotNull Long confirmedBriefVersionId,
                               @NotNull Long regulatoryBoundaryVersionId) { }
}
