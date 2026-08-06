package com.aivle.backend.project.dto.response;
import com.aivle.backend.common.entity.ProjectStage;
import com.aivle.backend.common.entity.ProjectStatus;
public record ProjectProgressResponse(Long projectId, ProjectStage stage, ProjectStatus status, int progress) {}
