package com.aivle.backend.project.controller;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.project.dto.request.*;
import com.aivle.backend.project.dto.response.*;
import com.aivle.backend.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> create(@Valid @RequestBody CreateProjectRequest body,
                                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                projectService.create(currentUserProvider.currentUserId(), body), request.getHeader("X-Request-Id")));
    }

    @GetMapping
    public ApiResponse<List<ProjectSummaryResponse>> findAll(HttpServletRequest request) {
        return ApiResponse.success(projectService.findAll(currentUserProvider.currentUserId()), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectDetailResponse> find(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(projectService.find(currentUserProvider.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @PatchMapping("/{projectId}")
    public ApiResponse<ProjectDetailResponse> update(@PathVariable Long projectId,
                                                      @Valid @RequestBody UpdateProjectRequest body,
                                                      HttpServletRequest request) {
        return ApiResponse.success(projectService.update(currentUserProvider.currentUserId(), projectId, body),
                request.getHeader("X-Request-Id"));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, HttpServletRequest request) {
        projectService.delete(currentUserProvider.currentUserId(), projectId, request.getHeader("X-Request-Id"));
        return ResponseEntity.noContent().build();
    }
}
