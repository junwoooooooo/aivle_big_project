package com.aivle.backend.taskrun.api;

import java.time.Instant;

public record ProjectJobView(
    String jobId,
    String taskRunId,
    String taskType,
    String subjectType,
    String subjectId,
    String status,
    String rawStatus,
    boolean actionable,
    String presentationStatus,
    String titleKey,
    String messageKey,
    String module,
    Instant startedAt,
    Instant updatedAt,
    boolean latestForSubject,
    boolean terminal,
    boolean retryable,
    String targetRoute
) { }
