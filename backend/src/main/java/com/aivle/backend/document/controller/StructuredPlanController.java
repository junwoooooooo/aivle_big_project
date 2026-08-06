package com.aivle.backend.document.controller;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.document.application.StructuredPlanQueryService;
import com.aivle.backend.document.application.StructuredPlanCommandService;
import com.aivle.backend.document.dto.request.ConfirmStructuredPlanRequest;
import com.aivle.backend.document.dto.request.UpdateMissingFieldRequest;
import com.aivle.backend.document.dto.response.StructuredMissingFieldResponse;
import com.aivle.backend.document.dto.response.StructuredPlanResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StructuredPlanController {
    private final StructuredPlanQueryService queryService;
    private final StructuredPlanCommandService commandService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/api/v1/projects/{projectId}/structured-plans/latest")
    public ApiResponse<StructuredPlanResponse> findLatest(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            queryService.findLatest(currentUserProvider.currentUserId(), projectId),
            request.getHeader("X-Request-Id")
        );
    }

    @PatchMapping(
        "/api/v1/projects/{projectId}/structured-plans/{planId}/missing-fields/{fieldId}"
    )
    public ApiResponse<StructuredMissingFieldResponse> updateMissingField(
        @PathVariable Long projectId,
        @PathVariable Long planId,
        @PathVariable Long fieldId,
        @Valid @RequestBody UpdateMissingFieldRequest body,
        HttpServletRequest request
    ) {
        String requestId = request.getHeader("X-Request-Id");
        return ApiResponse.success(commandService.updateMissingField(
            currentUserProvider.currentUserId(),
            projectId,
            planId,
            fieldId,
            body,
            requestId
        ), requestId);
    }

    @PostMapping(
        "/api/v1/projects/{projectId}/structured-plans/{planId}/confirm"
    )
    public ApiResponse<StructuredPlanResponse> confirm(
        @PathVariable Long projectId,
        @PathVariable Long planId,
        @Valid @RequestBody ConfirmStructuredPlanRequest body,
        HttpServletRequest request
    ) {
        String requestId = request.getHeader("X-Request-Id");
        return ApiResponse.success(commandService.confirm(
            currentUserProvider.currentUserId(),
            projectId,
            planId,
            body.version(),
            requestId
        ), requestId);
    }
}
