package com.aivle.backend.pipeline.marketinterview;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/market-interview")
@RequiredArgsConstructor
public class MarketInterviewController {
    private final MarketInterviewService service;
    private final CurrentUserProvider currentUser;

    @GetMapping("/board")
    public ApiResponse<JsonNode> board(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.board(currentUser.currentUserId(), projectId), requestId(request));
    }

    @GetMapping("/current")
    public ApiResponse<MarketInterviewService.CurrentView> current(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), requestId(request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MarketInterviewService.CurrentView>> start(@PathVariable Long projectId,
            @RequestBody(required = false) StartRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.start(currentUser.currentUserId(), projectId, request.getHeader("Idempotency-Key"),
                requestId(request), body == null ? null : body.conceptBoard(),
                body == null || body.sampleSize() == null ? 40 : body.sampleSize()), requestId(request)));
    }

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<MarketInterviewService.CurrentView>> retry(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.retry(currentUser.currentUserId(), projectId, request.getHeader("Idempotency-Key"),
                requestId(request)), requestId(request)));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }

    public record StartRequest(JsonNode conceptBoard, Integer sampleSize) { }
}
