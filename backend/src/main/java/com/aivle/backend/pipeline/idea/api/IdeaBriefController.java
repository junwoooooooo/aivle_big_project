package com.aivle.backend.pipeline.idea.api;

import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.idea.application.IdeaBriefService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/idea-brief")
@RequiredArgsConstructor
public class IdeaBriefController {
    private final IdeaBriefService service;
    private final CurrentUserProvider currentUser;

    @GetMapping
    public ApiResponse<IdeaBriefResponse> get(@PathVariable Long projectId, HttpServletRequest request) {
        return success(service.get(currentUser.currentUserId(), projectId), request);
    }

    @PostMapping("/derive")
    public ResponseEntity<ApiResponse<IdeaBriefResponse>> derive(
        @PathVariable Long projectId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody DeriveRequest body,
        HttpServletRequest request
    ) {
        IdeaBriefResponse response = service.derive(
            currentUser.currentUserId(), projectId, body, idempotencyKey, request.getHeader("X-Request-Id")
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(success(response, request));
    }

    @PatchMapping("/fields")
    public ApiResponse<IdeaBriefResponse> patchFields(
        @PathVariable Long projectId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PatchFieldsRequest body,
        HttpServletRequest request
    ) {
        return success(service.patchFields(currentUser.currentUserId(), projectId, body, idempotencyKey), request);
    }

    @PatchMapping("/interpretation")
    public ApiResponse<IdeaBriefResponse> patchInterpretation(
        @PathVariable Long projectId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PatchInterpretationRequest body,
        HttpServletRequest request
    ) {
        return success(service.patchInterpretation(
            currentUser.currentUserId(), projectId, body, idempotencyKey), request);
    }

    @PatchMapping("/commitments")
    public ApiResponse<IdeaBriefResponse> reviewCommitments(
        @PathVariable Long projectId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ReviewCommitmentsRequest body,
        HttpServletRequest request
    ) {
        return success(service.reviewCommitments(
            currentUser.currentUserId(), projectId, body, idempotencyKey), request);
    }

    @PostMapping("/answers")
    public ApiResponse<IdeaBriefResponse> answer(
        @PathVariable Long projectId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody AnswersRequest body,
        HttpServletRequest request
    ) {
        return success(service.answer(currentUser.currentUserId(), projectId, body, idempotencyKey), request);
    }

    @PostMapping("/reanalyze")
    public ResponseEntity<ApiResponse<IdeaBriefResponse>> reanalyze(
        @PathVariable Long projectId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        IdeaBriefResponse response = service.reanalyze(
            currentUser.currentUserId(), projectId, idempotencyKey
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(success(response, request));
    }

    @PostMapping("/confirm")
    public ApiResponse<IdeaBriefResponse> confirm(
        @PathVariable Long projectId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ConfirmRequest body,
        HttpServletRequest request
    ) {
        return success(service.confirm(currentUser.currentUserId(), projectId, body, idempotencyKey), request);
    }

    @PostMapping("/confirm-interpretation")
    public ApiResponse<IdeaBriefResponse> confirmInterpretation(
        @PathVariable Long projectId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ConfirmRequest body,
        HttpServletRequest request
    ) {
        return success(service.confirm(currentUser.currentUserId(), projectId, body, idempotencyKey), request);
    }

    private ApiResponse<IdeaBriefResponse> success(IdeaBriefResponse response, HttpServletRequest request) {
        return ApiResponse.success(response, request.getHeader("X-Request-Id"));
    }
}
