package com.aivle.backend.analysis.legal.controller;

import com.aivle.backend.analysis.legal.application.*;
import com.aivle.backend.analysis.legal.dto.*;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/legal-reviews")
@RequiredArgsConstructor
public class LegalReviewController {
    private final LegalReviewCommandService commands;
    private final LegalReviewQueryService queries;
    private final CurrentUserProvider currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<LegalReviewStartResponse>> start(
        @PathVariable Long projectId, HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            commands.start(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id")));
    }

    @GetMapping("/latest")
    public ApiResponse<LegalReviewResponse> latest(
        @PathVariable Long projectId, HttpServletRequest request
    ) {
        return ApiResponse.success(
            queries.latest(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }
}
