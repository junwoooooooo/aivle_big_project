package com.aivle.backend.validation.panel;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.validation.PersonaValidationTypes.InterviewPurpose;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/panel-interviews")
@RequiredArgsConstructor
public class PanelInterviewController {
    private final PanelInterviewService service;
    private final CurrentUserProvider currentUser;

    @GetMapping
    ApiResponse<List<PanelInterviewService.SummaryResponse>> list(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.list(currentUser.currentUserId(), projectId),
            requestId(request)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PanelInterviewService.DetailResponse> create(
        @PathVariable Long projectId,
        @Valid @RequestBody Request body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.create(currentUser.currentUserId(), projectId, body.command(), requestId(request)),
            requestId(request)
        );
    }

    @GetMapping("/{interviewId}")
    ApiResponse<PanelInterviewService.DetailResponse> detail(
        @PathVariable Long projectId,
        @PathVariable Long interviewId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.detail(currentUser.currentUserId(), projectId, interviewId),
            requestId(request)
        );
    }

    @PatchMapping("/{interviewId}")
    ApiResponse<PanelInterviewService.DetailResponse> update(
        @PathVariable Long projectId,
        @PathVariable Long interviewId,
        @Valid @RequestBody Request body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.update(currentUser.currentUserId(), projectId, interviewId, body.command(), requestId(request)),
            requestId(request)
        );
    }

    @PostMapping("/{interviewId}/run")
    ApiResponse<PanelInterviewService.DetailResponse> run(
        @PathVariable Long projectId,
        @PathVariable Long interviewId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.run(currentUser.currentUserId(), projectId, interviewId, requestId(request)),
            requestId(request)
        );
    }

    @DeleteMapping("/{interviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
        @PathVariable Long projectId,
        @PathVariable Long interviewId,
        HttpServletRequest request
    ) {
        service.delete(currentUser.currentUserId(), projectId, interviewId, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader("X-Request-Id");
    }

    record Request(
        @NotBlank @Size(max = 200) String title,
        @NotNull InterviewPurpose purpose,
        @NotNull List<@NotNull Long> personaIds,
        @NotNull List<@NotBlank String> questions
    ) {
        PanelInterviewService.Command command() {
            return new PanelInterviewService.Command(title, purpose, personaIds, questions);
        }
    }
}
