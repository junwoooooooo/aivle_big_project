package com.aivle.backend.project.dto.response;
import com.aivle.backend.common.entity.ProjectStage;
import com.aivle.backend.common.entity.ProjectStatus;
import java.time.LocalDateTime;
public record ProjectSummaryResponse(Long id, String title, String industryCategory,
                                     ProjectStage stage, ProjectStatus status,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {}
