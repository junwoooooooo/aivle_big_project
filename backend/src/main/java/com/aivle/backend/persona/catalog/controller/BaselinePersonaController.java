package com.aivle.backend.persona.catalog.controller;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.persona.catalog.application.BaselinePersonaQueryService;
import com.aivle.backend.persona.catalog.dto.BaselinePersonaResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/personas")
@RequiredArgsConstructor
public class BaselinePersonaController {
    private final BaselinePersonaQueryService queries;

    @GetMapping("/catalog")
    public ApiResponse<List<BaselinePersonaResponse>> catalog(HttpServletRequest request) {
        return ApiResponse.success(queries.catalog(), request.getHeader("X-Request-Id"));
    }
}
