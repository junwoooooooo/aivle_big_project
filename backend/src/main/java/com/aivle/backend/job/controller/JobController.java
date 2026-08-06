package com.aivle.backend.job.controller;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.job.dto.response.JobResponse;
import com.aivle.backend.job.service.JobQueryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/jobs") @RequiredArgsConstructor
public class JobController {
    private final JobQueryService service;
    private final CurrentUserProvider currentUserProvider;
    @GetMapping("/{jobId}")
    public ApiResponse<JobResponse> find(@PathVariable Long jobId, HttpServletRequest request) {
        return ApiResponse.success(service.find(currentUserProvider.currentUserId(), jobId), request.getHeader("X-Request-Id"));
    }
}
