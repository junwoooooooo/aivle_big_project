package com.aivle.backend.pipeline.businessvalidation;

/** Exact, already-committed source identity used only to schedule downstream proposal work. */
public record BusinessValidationCompletedEvent(
    String sessionId,
    Long projectId,
    Long ownerId,
    Long marketVersionId,
    Long bmVersionId,
    String marketSeedSnapshotId,
    Long selectionId,
    Integer selectionRevision,
    Integer bmPlanRevision
) { }
