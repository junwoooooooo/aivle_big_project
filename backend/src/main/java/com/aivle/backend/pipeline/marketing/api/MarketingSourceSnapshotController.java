package com.aivle.backend.pipeline.marketing.api;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.marketing.application.MarketingSourceSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/marketing-source-snapshots")
@RequiredArgsConstructor
public class MarketingSourceSnapshotController {
    private final MarketingSourceSnapshotService service;
    private final CurrentUserProvider user;
    @PostMapping("/finalize")
    public ResponseEntity<ApiResponse<MarketingSourceApiModels.SnapshotView>> finalizeSnapshot(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.finalizeSnapshot(user.currentUserId(), projectId), request.getHeader("X-Request-Id")));
    }
    @GetMapping("/current")
    public ApiResponse<MarketingSourceApiModels.SnapshotView> current(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(user.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }
}
