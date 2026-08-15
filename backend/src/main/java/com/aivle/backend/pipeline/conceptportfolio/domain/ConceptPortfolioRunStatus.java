package com.aivle.backend.pipeline.conceptportfolio.domain;

public enum ConceptPortfolioRunStatus {
    QUEUED,
    RUNNING,
    RESULTS_AVAILABLE,
    RESULTS_WITH_OPEN_INPUT,
    NEEDS_INPUT,
    FAILED,
    STALE
}
