package com.aivle.backend.persona.catalog.controller;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.persona.catalog.application.ProjectPersonaCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/personas")
@RequiredArgsConstructor
public class ProjectPersonaCatalogController {
    private final ProjectPersonaCatalogService personas;
    private final CurrentUserProvider currentUser;

    @GetMapping("/available")
    public ApiResponse<ProjectPersonaCatalogService.AvailablePersonasResponse> available(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            personas.available(currentUser.currentUserId(), projectId),
            requestId(request)
        );
    }

    @PutMapping("/selection")
    public ApiResponse<ProjectPersonaCatalogService.AvailablePersonasResponse> select(
        @PathVariable Long projectId,
        @Valid @RequestBody SelectionRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            personas.select(
                currentUser.currentUserId(),
                projectId,
                body.personaId(),
                requestId(request)
            ),
            requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader("X-Request-Id");
    }

    public record SelectionRequest(@NotNull Long personaId) { }
}
