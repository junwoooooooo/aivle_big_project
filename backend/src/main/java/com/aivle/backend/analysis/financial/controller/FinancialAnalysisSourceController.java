package com.aivle.backend.analysis.financial.controller;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.analysis.financial.service.FinancialAnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/financial-analysis")
@RequiredArgsConstructor
public class FinancialAnalysisSourceController {
    private final FinancialAnalysisService financial;
    private final CurrentUserProvider currentUser;

    @GetMapping("/source")
    public ApiResponse<FinancialAnalysisService.SourceResponse> source(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            financial.source(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id")
        );
    }
}
