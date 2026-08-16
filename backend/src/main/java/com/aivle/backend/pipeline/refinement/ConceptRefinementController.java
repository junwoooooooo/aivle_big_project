package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.common.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/business-validation/refinement")
@RequiredArgsConstructor
public class ConceptRefinementController {
    private final ConceptRefinementService refinement;
    private final CurrentUserProvider currentUser;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<ConceptRefinementService.CurrentView>> start(
            @PathVariable Long projectId, HttpServletRequest request) {
        return accepted(refinement.start(currentUser.currentUserId(), projectId,
            request.getHeader("Idempotency-Key"), id(request)), request);
    }

    @GetMapping("/current")
    public ApiResponse<ConceptRefinementService.CurrentView> current(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(refinement.current(currentUser.currentUserId(), projectId), id(request));
    }

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<ConceptRefinementService.CurrentView>> retry(
            @PathVariable Long projectId, HttpServletRequest request) {
        return accepted(refinement.retry(currentUser.currentUserId(), projectId,
            request.getHeader("Idempotency-Key"), id(request)), request);
    }

    private ResponseEntity<ApiResponse<ConceptRefinementService.CurrentView>> accepted(
            ConceptRefinementService.CurrentView value, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(value, id(request)));
    }

    private String id(HttpServletRequest request) { return RequestIds.resolve(request); }
}
