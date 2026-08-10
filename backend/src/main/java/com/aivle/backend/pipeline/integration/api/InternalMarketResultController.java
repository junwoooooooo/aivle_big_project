package com.aivle.backend.pipeline.integration.api;

import static com.aivle.backend.pipeline.integration.api.MarketResultApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.pipeline.integration.application.InternalModuleAuthenticator;
import com.aivle.backend.pipeline.integration.application.MarketResultIntakeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/internal/market-results")
@RequiredArgsConstructor
public class InternalMarketResultController {
    private final MarketResultIntakeService service;
    private final InternalModuleAuthenticator authenticator;
    @PostMapping
    public ApiResponse<MarketResultView> receive(@RequestHeader(name = "X-Internal-Api-Key", required = false) String key,
            @Valid @RequestBody MarketResultIntakeRequest body, HttpServletRequest request) {
        authenticator.require(key);
        return ApiResponse.success(service.intake(body), request.getHeader("X-Request-Id"));
    }
}
