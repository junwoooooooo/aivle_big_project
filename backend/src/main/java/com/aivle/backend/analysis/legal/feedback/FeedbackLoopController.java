package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 피드백 루프: 수정 승인/기각, 질문 답변, 발행, 사용자 편집. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class FeedbackLoopController {
    private final PlanVersionService planVersions;
    private final PublicationService publicationService;
    private final FeedbackQueryService queries;
    private final CurrentUserProvider currentUser;

    public record AcceptSuggestionRequest(Long suggestionId) {}

    public record AnswerQuestionRequest(String answer, String factKey, String source) {}

    public record PublishRequest(List<String> completedActions) {}

    public record EditPlanRequest(List<PlanVersionService.SectionEdit> sections) {}

    @PostMapping("/revision-requests/{requestId}/accept")
    public ApiResponse<PlanVersionService.PlanVersionCreated> accept(
        @PathVariable Long projectId, @PathVariable Long requestId,
        @RequestBody AcceptSuggestionRequest body, HttpServletRequest request
    ) {
        return ApiResponse.success(
            planVersions.acceptSuggestion(
                currentUser.currentUserId(), projectId, requestId, body.suggestionId()),
            request.getHeader("X-Request-Id"));
    }

    @PostMapping("/revision-requests/{requestId}/dismiss")
    public ApiResponse<Void> dismiss(
        @PathVariable Long projectId, @PathVariable Long requestId, HttpServletRequest request
    ) {
        planVersions.dismissRequest(currentUser.currentUserId(), projectId, requestId);
        return ApiResponse.success(null, request.getHeader("X-Request-Id"));
    }

    @PostMapping("/legal-questions/{questionId}/answer")
    public ApiResponse<PlanVersionService.PlanVersionCreated> answer(
        @PathVariable Long projectId, @PathVariable Long questionId,
        @RequestBody AnswerQuestionRequest body, HttpServletRequest request
    ) {
        return ApiResponse.success(
            planVersions.answerQuestion(
                currentUser.currentUserId(), projectId, questionId,
                body.answer(), body.factKey(), body.source()),
            request.getHeader("X-Request-Id"));
    }

    @PostMapping("/review-cycles/{cycleId}/publish")
    public ApiResponse<PublicationService.Published> publish(
        @PathVariable Long projectId, @PathVariable Long cycleId,
        @RequestBody PublishRequest body, HttpServletRequest request
    ) {
        return ApiResponse.success(
            publicationService.publish(
                currentUser.currentUserId(), projectId, cycleId, body.completedActions()),
            request.getHeader("X-Request-Id"));
    }

    @PostMapping("/structured-plans/{planId}/edit")
    public ApiResponse<PlanVersionService.PlanVersionCreated> edit(
        @PathVariable Long projectId, @PathVariable Long planId,
        @RequestBody EditPlanRequest body, HttpServletRequest request
    ) {
        return ApiResponse.success(
            planVersions.userEdit(currentUser.currentUserId(), projectId, planId, body.sections()),
            request.getHeader("X-Request-Id"));
    }

    @GetMapping("/review-cycles/active")
    public ApiResponse<FeedbackQueryService.ActiveCycleResponse> activeCycle(
        @PathVariable Long projectId, HttpServletRequest request
    ) {
        return ApiResponse.success(
            queries.activeCycle(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }

    @GetMapping("/plan-versions")
    public ApiResponse<List<FeedbackQueryService.PlanVersionItem>> planVersions(
        @PathVariable Long projectId, HttpServletRequest request
    ) {
        return ApiResponse.success(
            queries.planVersions(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }

    @GetMapping("/publications/latest")
    public ApiResponse<FeedbackQueryService.PublicationResponse> latestPublication(
        @PathVariable Long projectId, HttpServletRequest request
    ) {
        return ApiResponse.success(
            queries.latestPublication(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }
}
