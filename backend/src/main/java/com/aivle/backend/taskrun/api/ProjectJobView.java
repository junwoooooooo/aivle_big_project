package com.aivle.backend.taskrun.api;

import java.time.LocalDateTime;

public record ProjectJobView(
    String jobId,
    String taskRunId,
    String taskType,
    String subjectType,
    String subjectId,
    String status,
    String titleKey,
    String messageKey,
    String module,
    LocalDateTime startedAt,
    LocalDateTime updatedAt,
    boolean terminal,
    boolean retryable,
    String targetRoute
) { }
