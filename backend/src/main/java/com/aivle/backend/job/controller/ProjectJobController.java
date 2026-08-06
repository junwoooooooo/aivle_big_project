package com.aivle.backend.job.controller;

import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.job.dto.response.JobResponse;
import com.aivle.backend.job.service.JobQueryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProjectJobController {
    private final JobQueryService service;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/api/v1/projects/{projectId}/jobs/latest")
    public ApiResponse<JobResponse> findLatest(
        @PathVariable Long projectId,
        @RequestParam(defaultValue = "DOCUMENT_PARSE") JobType jobType,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.findLatest(currentUserProvider.currentUserId(), projectId, jobType),
            request.getHeader("X-Request-Id")
        );
    }
}
