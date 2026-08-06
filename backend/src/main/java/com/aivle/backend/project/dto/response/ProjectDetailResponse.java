package com.aivle.backend.project.dto.response;
import com.aivle.backend.common.entity.ProjectStatus;
import java.time.LocalDateTime;
public record ProjectDetailResponse(Long id, Long ownerId, String title, String description,
                                    String industryCategory, ProjectStatus status,
                                    LocalDateTime startedAt, LocalDateTime completedAt,
                                    LocalDateTime createdAt, LocalDateTime updatedAt, Long version) {}
