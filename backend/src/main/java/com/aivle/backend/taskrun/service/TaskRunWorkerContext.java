package com.aivle.backend.taskrun.service;

import com.aivle.backend.taskrun.domain.TaskType;

/** Immutable scalar snapshot captured while the TaskRun persistence context is active. */
public record TaskRunWorkerContext(
        String taskRunId,
        Long projectId,
        Long ownerId,
        TaskType taskType,
        String subjectType,
        String subjectId,
        String inputSnapshot,
        String inputHash,
        String idempotencyKey,
        String correlationId,
        String contractVersion,
        String taskSchemaVersion,
        String locale,
        int attemptCount,
        int maxAttempts) {
}
