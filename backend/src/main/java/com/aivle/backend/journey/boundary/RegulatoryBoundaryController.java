package com.aivle.backend.journey.boundary;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/projects/{projectId}/regulatory-boundaries")
@RequiredArgsConstructor
public class RegulatoryBoundaryController {
    private final CurrentUserProvider currentUser;
    private final RegulatoryBoundaryApplicationService service;

    @PostMapping
    public ResponseEntity<ApiResponse<RegulatoryBoundaryApplicationService.StartView>> start(
            @PathVariable Long projectId, @RequestBody StartRequest body, HttpServletRequest request) {
        var result = service.start(currentUser.currentUserId(), projectId, body.confirmedBriefVersionId());
        return ResponseEntity.status(result.jobId() == null ? 200 : 202)
            .body(ApiResponse.success(result, requestId(request)));
    }
    @GetMapping("/current")
    public ApiResponse<RegulatoryBoundaryApplicationService.CurrentView> current(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), requestId(request));
    }
    @GetMapping("/{boundaryVersionId}")
    public ApiResponse<RegulatoryBoundaryApplicationService.VersionView> version(@PathVariable Long projectId,
            @PathVariable Long boundaryVersionId, HttpServletRequest request) {
        return ApiResponse.success(service.version(currentUser.currentUserId(), projectId, boundaryVersionId), requestId(request));
    }
    @GetMapping("/runs/{runId}")
    public ApiResponse<RegulatoryBoundaryApplicationService.RunView> run(@PathVariable Long projectId,
            @PathVariable Long runId, HttpServletRequest request) {
        return ApiResponse.success(service.run(currentUser.currentUserId(), projectId, runId), requestId(request));
    }
    private String requestId(HttpServletRequest request) { return request.getHeader("X-Request-Id"); }
    public record StartRequest(Long confirmedBriefVersionId) { }
}
