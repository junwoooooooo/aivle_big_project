package com.aivle.backend.pipeline.concept.domain;

public enum ConceptFactoryRunStatus {
    QUEUED, GENERATING, VALIDATING, REPLACING, NEEDS_INPUT, COMPLETED, FAILED, STALE
}
