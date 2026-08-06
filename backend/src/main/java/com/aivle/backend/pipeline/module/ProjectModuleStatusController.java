package com.aivle.backend.pipeline.module;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/modules")
@RequiredArgsConstructor
public class ProjectModuleStatusController {
    private final ProjectModuleStatusService moduleStatusService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<ProjectModuleStatusResponse>> findAll(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            moduleStatusService.findAll(currentUserProvider.currentUserId(), projectId),
            request.getHeader("X-Request-Id")
        );
    }
}
