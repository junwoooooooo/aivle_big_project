package com.aivle.backend.pipeline.marketseed.api;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedSnapshotService;
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
@RequestMapping("/api/v3/projects/{projectId}/market-analysis-seed-snapshots")
@RequiredArgsConstructor
public class MarketAnalysisSeedSnapshotController {
    private final MarketAnalysisSeedSnapshotService service;
    private final CurrentUserProvider currentUser;

    @PostMapping("/finalize")
    public ResponseEntity<ApiResponse<MarketAnalysisSeedApiModels.SnapshotView>> finalizeSnapshot(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.finalizeSnapshot(currentUser.currentUserId(), projectId), request.getHeader("X-Request-Id")));
    }

    @GetMapping("/current")
    public ApiResponse<MarketAnalysisSeedApiModels.SnapshotView> current(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }
}
