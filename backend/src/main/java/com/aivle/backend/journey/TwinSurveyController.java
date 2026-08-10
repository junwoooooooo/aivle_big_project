package com.aivle.backend.journey;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * 여정 — 재무분석 → 「패널 트윈 조사」 → 마케팅.
 *
 * <p>202 로 즉시 돌려주고 화면이 {@code /current} 를 폴링한다. n=300 이면 분 단위라
 * 동기로 줄 방법이 없다.
 */
@RestController
@RequestMapping("/api/v2/projects/{projectId}")
@RequiredArgsConstructor
public class TwinSurveyController {

    private final TwinSurveyService service;
    private final CurrentUserProvider currentUser;

    @PostMapping("/twin-survey")
    public ResponseEntity<ApiResponse<TwinSurveyService.RunView>> start(
            @PathVariable Long projectId, @Valid @RequestBody StartRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.start(currentUser.currentUserId(), projectId,
                body.situation(), body.pairs(), body.sampleSize()), id(request)));
    }

    @GetMapping("/twin-survey/current")
    public ApiResponse<TwinSurveyService.CurrentView> current(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), id(request));
    }

    /**
     * {@code pairs} 는 계약 그대로 넘어간다 — 백엔드가 다시 가공하지 않는다.
     * 자극의 판매 가능 여부는 AI 쪽 게이트가 <b>LLM 호출 전에</b> 정한다.
     */
    public record StartRequest(@NotBlank String situation, @NotNull JsonNode pairs,
                               @NotNull Integer sampleSize) { }

    private String id(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }
}
