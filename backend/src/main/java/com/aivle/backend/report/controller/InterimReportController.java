package com.aivle.backend.report.controller;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.report.dto.InterimReportResponse;
import com.aivle.backend.report.service.InterimReportService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class InterimReportController {

    private final InterimReportService interimReportService;

    @GetMapping("/{projectId}/reports/interim")
    public ResponseEntity<ApiResponse<InterimReportResponse>> getInterimReport(
            @PathVariable Long projectId,
            HttpServletRequest request) {

        InterimReportResponse response = interimReportService.generateInterimReport(projectId);
        return ResponseEntity.ok(ApiResponse.success(response, request.getHeader("X-Request-Id")));
    }
}
