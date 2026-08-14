package com.aivle.backend.taskrun.api;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.taskrun.service.ProjectJobQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectJobController {
    private final ProjectJobQueryService jobs;
    private final CurrentUserProvider users;

    @GetMapping("/active-jobs")
    public ApiResponse<List<ProjectJobView>> active(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(jobs.active(users.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/recent-jobs")
    public ApiResponse<List<ProjectJobView>> recent(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(jobs.recent(users.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/jobs/history")
    public ApiResponse<ProjectJobHistoryResponse> history(@PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(jobs.history(users.currentUserId(), projectId, page, size),
            request.getHeader("X-Request-Id"));
    }
}
