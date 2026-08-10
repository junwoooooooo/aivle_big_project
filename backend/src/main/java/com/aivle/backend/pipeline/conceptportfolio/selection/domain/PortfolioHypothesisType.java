package com.aivle.backend.pipeline.conceptportfolio.selection.domain;

public enum PortfolioHypothesisType {
    TARGET_REGION(true),
    REVENUE_MODEL(true),
    PRICE(true),
    CHANNELS(true),
    DIFFERENTIATORS(true),
    PRE_MARKET_SOM_SHARE(false),
    PRE_MARKET_SOM(false);

    private final boolean legalSensitive;
    PortfolioHypothesisType(boolean legalSensitive) { this.legalSensitive = legalSensitive; }
    public boolean legalSensitive() { return legalSensitive; }
}
