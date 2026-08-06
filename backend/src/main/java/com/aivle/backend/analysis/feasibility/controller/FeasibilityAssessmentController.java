package com.aivle.backend.analysis.feasibility.controller;

import com.aivle.backend.analysis.feasibility.application.*;
import com.aivle.backend.analysis.feasibility.dto.*;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/feasibility-assessments")
@RequiredArgsConstructor
public class FeasibilityAssessmentController {
    private final FeasibilityCommandService commands;
    private final FeasibilityQueryService queries;
    private final CurrentUserProvider currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<FeasibilityStartResponse>> start(
        @PathVariable Long projectId, HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            commands.start(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id")));
    }

    @GetMapping("/latest")
    public ApiResponse<FeasibilityAssessmentResponse> latest(
        @PathVariable Long projectId, HttpServletRequest request
    ) {
        return ApiResponse.success(
            queries.latest(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }
}
