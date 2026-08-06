package com.aivle.backend.persona.recommendation.controller;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.persona.recommendation.application.*;
import com.aivle.backend.persona.recommendation.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/persona-recommendations")
@RequiredArgsConstructor
public class PersonaRecommendationController {
    private final PersonaRecommendationCommandService commands;
    private final PersonaRecommendationQueryService queries;
    private final CurrentUserProvider currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<PersonaStartResponse>> start(
        @PathVariable Long projectId, HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            commands.start(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id")));
    }

    @GetMapping("/latest")
    public ApiResponse<PersonaRecommendationResponse> latest(
        @PathVariable Long projectId, HttpServletRequest request
    ) {
        return ApiResponse.success(
            queries.latest(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }
}
