package com.aivle.backend.validation;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/validation-personas")
@RequiredArgsConstructor
public class PersonaValidationController {
    private final PersonaValidationSourceService source;
    private final CurrentUserProvider currentUser;

    @GetMapping
    public ApiResponse<PersonaValidationSourceService.CandidateResponse> candidates(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            source.candidates(currentUser.currentUserId(), projectId),
            request.getHeader("X-Request-Id")
        );
    }
}
