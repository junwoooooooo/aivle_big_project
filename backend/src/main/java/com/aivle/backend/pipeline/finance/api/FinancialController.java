package com.aivle.backend.pipeline.finance.api;

import static com.aivle.backend.pipeline.finance.api.FinancialApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.finance.application.FinancialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/finance")
@RequiredArgsConstructor
public class FinancialController {
    private final FinancialService service;
    private final CurrentUserProvider user;

    @PostMapping("/preparation/initialize")
    public ResponseEntity<ApiResponse<PreparationView>> initialize(@PathVariable Long projectId, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.initialize(user.currentUserId(), projectId), request.getHeader("X-Request-Id")));
    }

    @GetMapping("/preparation")
    public ApiResponse<PreparationView> preparation(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(user.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @PatchMapping("/preparation")
    public ApiResponse<PreparationView> patch(@PathVariable Long projectId, @Valid @RequestBody FinancialFieldsPatch body,
            HttpServletRequest request) {
        return ApiResponse.success(service.patchFields(user.currentUserId(), projectId, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/input-snapshots/finalize")
    public ResponseEntity<ApiResponse<SnapshotView>> finalizeSnapshot(@PathVariable Long projectId, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.finalizeSnapshot(user.currentUserId(), projectId), request.getHeader("X-Request-Id")));
    }

    @GetMapping("/input-snapshots/current")
    public ApiResponse<SnapshotView> currentSnapshot(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.currentSnapshot(user.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }
}
