package com.aivle.backend.pipeline.businessvalidation;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.common.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/business-validation")
@RequiredArgsConstructor
public class BusinessValidationController {

    private final BusinessValidationCoordinator coordinator;
    private final CurrentUserProvider currentUser;

    @GetMapping("/current")
    public ApiResponse<BusinessValidationCoordinator.CurrentView> current(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(coordinator.current(currentUser.currentUserId(), projectId), id(request));
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<BusinessValidationCoordinator.CurrentView>> start(
            @PathVariable Long projectId, @RequestBody(required = false) StartRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            coordinator.start(currentUser.currentUserId(), projectId,
                body == null ? null : body.asOf(), request.getHeader("Idempotency-Key"), id(request)), id(request)));
    }

    @PostMapping("/retry-bm")
    public ResponseEntity<ApiResponse<BusinessValidationCoordinator.CurrentView>> retryBm(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            coordinator.retryBm(currentUser.currentUserId(), projectId,
                request.getHeader("Idempotency-Key"), id(request)), id(request)));
    }

    public record StartRequest(String asOf) { }

    private String id(HttpServletRequest request) { return RequestIds.resolve(request); }
}
