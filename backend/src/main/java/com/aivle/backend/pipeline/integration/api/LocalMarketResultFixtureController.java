package com.aivle.backend.pipeline.integration.api;

import static com.aivle.backend.pipeline.integration.api.MarketResultApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.integration.application.MarketResultIntakeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test", "e2e"})
@RequestMapping("/api/v3/projects/{projectId}/market-results/fixture")
@RequiredArgsConstructor
public class LocalMarketResultFixtureController {
    private final MarketResultIntakeService service;
    private final CurrentUserProvider currentUser;
    @PostMapping
    public ApiResponse<MarketResultView> importFixture(@PathVariable Long projectId,
            @Valid @RequestBody MarketResultIntakeRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.importFixture(currentUser.currentUserId(), projectId, body), request.getHeader("X-Request-Id"));
    }
    @PostMapping("/stub")
    public ApiResponse<MarketResultView> createStub(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.createLocalStub(currentUser.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }
}
