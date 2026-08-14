package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.common.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * Project Shell의 Market Research와 Business Model Product API.
 *
 * <p>둘 다 <b>202 로 즉시 돌려주고</b> Job/Project SSE 뒤 {@code /current} 를 재조회한다.
 * 1단계는 90~266초라 동기로 줄 방법이 없다.
 */
@RestController
@RequestMapping("/api/v3/projects/{projectId}")
@RequiredArgsConstructor
public class MarketResearchController {

    private final MarketResearchService service;
    private final CurrentUserProvider currentUser;

    /** Market 실행. current authoritative Concept 스냅샷 직렬화는 서버가 한다. */
    @PostMapping("/market-research")
    public ResponseEntity<ApiResponse<MarketResearchService.RunView>> startFull(
            @PathVariable Long projectId, @Valid @RequestBody StartRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.startFull(currentUser.currentUserId(), projectId,
                body == null ? null : body.asOf(),
                request.getHeader("Idempotency-Key"), id(request)), id(request)));
    }

    @GetMapping("/market-research/current")
    public ApiResponse<MarketResearchService.CurrentView> currentFull(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId,
            MarketResearchRun.Kind.FULL), id(request));
    }

    @PostMapping("/market-research/recollect")
    public ResponseEntity<ApiResponse<MarketResearchService.RunView>> recollect(
            @PathVariable Long projectId, @Valid @RequestBody RecollectRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.startRecollect(currentUser.currentUserId(), projectId,
                body.sourceMarketResearchVersionId(), body.slots(), body.from(), body.slotsFrom(), body.asOf(),
                request.getHeader("Idempotency-Key"), id(request)), id(request)));
    }

    @GetMapping("/market-research/competitor-seeds")
    public ApiResponse<ResearchCompetitorSeedService.SeedsView> currentCompetitorSeeds(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.currentCompetitorSeeds(currentUser.currentUserId(), projectId), id(request));
    }

    @PutMapping("/market-research/competitor-seeds")
    public ApiResponse<ResearchCompetitorSeedService.SeedsView> saveCompetitorSeeds(
            @PathVariable Long projectId, @RequestBody JsonNode body, HttpServletRequest request) {
        return ApiResponse.success(service.saveCompetitorSeeds(currentUser.currentUserId(), projectId, body), id(request));
    }

    /** 정확한 MarketResearchVersion을 근거로 BM 캔버스를 만든다. */
    @PostMapping("/business-model")
    public ResponseEntity<ApiResponse<MarketResearchService.RunView>> startBm(
            @PathVariable Long projectId, @RequestBody(required = false) BmRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.startBm(currentUser.currentUserId(), projectId,
                request.getHeader("Idempotency-Key"), id(request)),
            id(request)));
    }

    @GetMapping("/business-model/current")
    public ApiResponse<MarketResearchService.CurrentView> currentBm(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId,
            MarketResearchRun.Kind.BM), id(request));
    }

    /**
     * BM 앞 단계 — <b>사용자가 채우는 실행 계획.</b>
     *
     * <p>계획 4칸(활동·자원·파트너·고객 관계)은 컨셉 계약이 주지 않는 값이라 여기서 받는다.
     * 요청 바디에 실어 실행과 함께 보내지 않는 이유는 <b>새로고침에 사라지고 감사 기록도
     * 안 남기 때문</b>이다 — 저장해 두고 실행이 읽는다.
     */
    @GetMapping("/business-model/plan")
    public ApiResponse<BmPlanPreparationService.PlanView> currentPlan(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(
            service.currentPlan(currentUser.currentUserId(), projectId), id(request));
    }

    @PatchMapping("/business-model/plan")
    public ApiResponse<BmPlanPreparationService.PlanView> savePlan(
            @PathVariable Long projectId, @RequestBody PlanRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.savePlan(currentUser.currentUserId(), projectId,
            body.plan(), body.constraints()), id(request));
    }

    /** 공식 요청은 기준일만 받고 Concept authority는 서버가 결정한다. */
    public record StartRequest(String asOf) { }

    public record RecollectRequest(
        @jakarta.validation.constraints.NotNull Long sourceMarketResearchVersionId,
        String slots, String from, String slotsFrom, String asOf) { }

    /** BM source는 서버가 current immutable Market version에서 결속한다. */
    public record BmRequest() { }

    /**
     * ⚠ 두 칸 모두 <b>필수가 아니다.</b> 전부 선택 입력이므로 빈 계획도 정상 요청이고,
     * 그때 캔버스는 그만큼 빈 채로 나온다 — 그 사실을 화면이 제출 전에 확인받는다.
     */
    public record PlanRequest(JsonNode plan, JsonNode constraints) { }

    private String id(HttpServletRequest request) {
        return RequestIds.resolve(request);
    }
}
