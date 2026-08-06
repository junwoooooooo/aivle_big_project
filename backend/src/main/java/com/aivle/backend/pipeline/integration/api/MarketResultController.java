package com.aivle.backend.pipeline.integration.api;

import static com.aivle.backend.pipeline.integration.api.MarketResultApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.integration.application.MarketResultIntakeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}")
@RequiredArgsConstructor
public class MarketResultController {
    private final MarketResultIntakeService service;
    private final CurrentUserProvider currentUser;

    @GetMapping("/market-result")
    public ApiResponse<MarketResultView> current(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }
    @PostMapping("/planning-change-proposals/{proposalId}/decision")
    public ApiResponse<MarketResultView> decide(@PathVariable Long projectId, @PathVariable String proposalId,
            @Valid @RequestBody ProposalDecisionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.decide(currentUser.currentUserId(), projectId, proposalId, body), request.getHeader("X-Request-Id"));
    }
}
