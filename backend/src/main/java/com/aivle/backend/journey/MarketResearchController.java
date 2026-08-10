package com.aivle.backend.journey;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * 여정 2단계 — 시장조사 → 「다음」 → BM 캔버스.
 *
 * <p>둘 다 <b>202 로 즉시 돌려주고</b> 화면이 {@code /current} 를 폴링한다.
 * 1단계는 90~266초라 동기로 줄 방법이 없다.
 */
@RestController
@RequestMapping("/api/v2/projects/{projectId}")
@RequiredArgsConstructor
public class MarketResearchController {

    private final MarketResearchService service;
    private final CurrentUserProvider currentUser;

    /** 1단계 실행. 컨셉 스냅샷을 그대로 받는다 — 직렬화는 서버가 한다. */
    @PostMapping("/market-research")
    public ResponseEntity<ApiResponse<MarketResearchService.RunView>> startFull(
            @PathVariable Long projectId, @Valid @RequestBody StartRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.startFull(currentUser.currentUserId(), projectId,
                body.concept(), body.conceptId(), body.asOf()), id(request)));
    }

    @GetMapping("/market-research/current")
    public ApiResponse<MarketResearchService.CurrentView> currentFull(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId,
            MarketResearchRun.Kind.FULL), id(request));
    }

    /** 2단계 — 「다음」. 1단계 결과를 근거로 캔버스를 만든다. */
    @PostMapping("/business-model")
    public ResponseEntity<ApiResponse<MarketResearchService.RunView>> startBm(
            @PathVariable Long projectId, @Valid @RequestBody BmRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.startBm(currentUser.currentUserId(), projectId, body.asOf()),
            id(request)));
    }

    @GetMapping("/business-model/current")
    public ApiResponse<MarketResearchService.CurrentView> currentBm(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId,
            MarketResearchRun.Kind.BM), id(request));
    }

    /** {@code conceptId} 는 AI 쪽 {@code pipeline.CONCEPTS} 의 <b>이름표</b>다. */
    public record StartRequest(@NotBlank String conceptId, @NotBlank String asOf, JsonNode concept) { }

    /**
     * {@code conceptId} 는 <b>받되 쓰지 않는다</b> — 2단계 컨셉은 1단계 결과에서 잇는다.
     * 클라이언트가 보낸 값으로 덮으면 「관측은 A, 잣대는 B」가 된다. 필드는 요청 모양을
     * 깨지 않으려고 남긴다.
     */
    public record BmRequest(@NotBlank String conceptId, @NotBlank String asOf) { }

    private String id(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }
}
