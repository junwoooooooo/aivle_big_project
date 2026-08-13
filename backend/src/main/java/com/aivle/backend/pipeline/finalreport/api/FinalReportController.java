package com.aivle.backend.pipeline.finalreport.api;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.finalreport.application.FinalReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/final-report")
@RequiredArgsConstructor
public class FinalReportController {
    private final FinalReportService reports;
    private final CurrentUserProvider users;

    @GetMapping
    public ApiResponse<FinalReportApiModels.FinalReportView> current(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ApiResponse.success(reports.current(users.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/generate")
    public ApiResponse<FinalReportApiModels.FinalReportView> generate(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ApiResponse.success(reports.generate(users.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }
}
