package com.aivle.backend.pipeline.selection.domain;

public enum HypothesisType {
    TARGET_REGION("targetRegion", true),
    REVENUE_MODEL("revenueModel", true),
    PRICE("price", true),
    CHANNELS("channels", true),
    DIFFERENTIATORS("differentiators", true),
    PRE_MARKET_SOM_SHARE("preMarketSomShareHypothesis", false),
    PRE_MARKET_SOM("preMarketSomHypothesis", false);

    private final String candidateField;
    private final boolean legalSensitive;

    HypothesisType(String candidateField, boolean legalSensitive) {
        this.candidateField = candidateField;
        this.legalSensitive = legalSensitive;
    }

    public String candidateField() { return candidateField; }
    public boolean legalSensitive() { return legalSensitive; }
}
