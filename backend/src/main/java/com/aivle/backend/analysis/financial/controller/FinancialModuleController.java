package com.aivle.backend.analysis.financial.controller;

import com.aivle.backend.analysis.financial.dto.FinancialModuleRequest;
import com.aivle.backend.analysis.financial.dto.FinancialModuleResponse;
import com.aivle.backend.analysis.financial.service.FinancialModuleService;
import com.aivle.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Stateless sandbox API for http://localhost:3001/module. Saved project analyses continue to use the project API. */
@RestController
@RequestMapping("/api/v1/modules/financial")
@RequiredArgsConstructor
public class FinancialModuleController {
    private final FinancialModuleService module;
    @PostMapping("/preview")
    public ApiResponse<FinancialModuleResponse> preview(@Valid @RequestBody FinancialModuleRequest request, HttpServletRequest http) {
        return ApiResponse.success(module.preview(request), http.getHeader("X-Request-Id"));
    }
}
