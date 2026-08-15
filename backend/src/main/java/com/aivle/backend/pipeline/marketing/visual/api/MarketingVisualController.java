package com.aivle.backend.pipeline.marketing.visual.api;

import static com.aivle.backend.pipeline.marketing.visual.api.MarketingVisualApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.marketing.visual.application.MarketingVisualService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/marketing-visual-runs")
@RequiredArgsConstructor
public class MarketingVisualController {
    private final MarketingVisualService service;
    private final CurrentUserProvider user;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<VisualRunView> create(@PathVariable Long projectId,
            @Valid @RequestBody CreateRequest body,
            @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        return ApiResponse.success(service.create(user.currentUserId(), projectId, body, key,
            request.getHeader("X-Correlation-Id")), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{taskRunId}")
    public ApiResponse<VisualRunView> get(@PathVariable Long projectId,
            @PathVariable String taskRunId, HttpServletRequest request) {
        return ApiResponse.success(service.get(user.currentUserId(), projectId, taskRunId),
            request.getHeader("X-Request-Id"));
    }

    @GetMapping("/current")
    public ApiResponse<VisualRunView> current(@PathVariable Long projectId,
            @RequestParam String marketingContentId, HttpServletRequest request) {
        return ApiResponse.success(service.current(user.currentUserId(), projectId, marketingContentId),
            request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{taskRunId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<VisualRunView> retry(@PathVariable Long projectId,
            @PathVariable String taskRunId, @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request) {
        return ApiResponse.success(service.retry(user.currentUserId(), projectId, taskRunId, key,
            request.getHeader("X-Correlation-Id")), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{taskRunId}/cancel")
    public ApiResponse<VisualRunView> cancel(@PathVariable Long projectId,
            @PathVariable String taskRunId, HttpServletRequest request) {
        return ApiResponse.success(service.cancel(user.currentUserId(), projectId, taskRunId),
            request.getHeader("X-Request-Id"));
    }
}
