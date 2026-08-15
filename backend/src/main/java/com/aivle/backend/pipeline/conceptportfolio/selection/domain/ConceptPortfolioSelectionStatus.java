package com.aivle.backend.pipeline.conceptportfolio.selection.domain;

public enum ConceptPortfolioSelectionStatus {
    HYPOTHESES_PREPARING,
    PENDING_HYPOTHESIS_CONFIRMATION,
    DELTA_LEGAL_PENDING,
    DELTA_LEGAL_FAILED,
    READY_FOR_LEGAL_REPORT,
    LEGAL_REPORT_READY,
    MARKET_SEED_FINALIZING,
    READY_FOR_MARKET,
    FAILED,
    STALE
}
