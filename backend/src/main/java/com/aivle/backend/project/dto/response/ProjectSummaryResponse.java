package com.aivle.backend.project.dto.response;
import com.aivle.backend.common.entity.ProjectStatus;
import java.time.LocalDateTime;
public record ProjectSummaryResponse(Long id, String title, String industryCategory,
                                     ProjectStatus status,
                                     LocalDateTime createdAt, LocalDateTime updatedAt,
                                     String currentJourneyLabel, int completedJourneyCount,
                                     String presentationState, int attentionCount,
                                     String attentionReason) {}
