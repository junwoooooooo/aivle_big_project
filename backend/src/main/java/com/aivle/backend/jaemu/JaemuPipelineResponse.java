package com.aivle.backend.jaemu;

import java.util.List;

public record JaemuPipelineResponse(
    IdeaSummary idea,
    LegalReview legalReview,
    ConceptInput concept,
    List<PipelineState> pipelineStates,
    MarketJoinData marketJoinData,
    MarketAnalysis market,
    BmAnalysis bm,
    List<FinancialInputSource> financialInputSources,
    JaemuAnalysisRequest financialInput,
    JaemuAnalysisResponse financial
) {
    public record IdeaSummary(String productName, String category, String targetCustomer, String problem, String valueProposition) { }

    public record LegalReview(String status, List<String> marketConstraints, List<String> requiredCertifications,
                              List<String> policyOpportunities) { }

    public record ConceptInput(String conceptName, String problem, String targetCustomer, String solution,
                               String coreValue, String category, List<String> alternatives,
                               List<String> differentiation, String revenueModel) { }

    public record PipelineState(String id, String label, String status, String owner, String output, List<String> checks) { }

    public record MarketJoinData(
        MarketSupplyDemand marketSupplyDemand,
        MarketSizeGrowth marketSizeGrowth,
        CompetitorPrice competitorPrice,
        Differentiation differentiation,
        MarketFinalSummary finalSummary
    ) { }

    public record MarketSupplyDemand(List<MarketMetric> metrics, List<String> warnings) { }

    public record MarketSizeGrowth(List<MarketMetric> metrics, List<GrowthCalculation> calculations) { }

    public record CompetitorPrice(List<CompetitorProduct> products, PriceSummary priceSummary, List<String> warnings) { }

    public record Differentiation(List<DifferentiationRow> comparison, List<String> candidates) { }

    public record MarketFinalSummary(String representativeMarketValue, double growthRate, String customerBase,
                                     int competitorCount, String priceRange, List<String> differentiationCandidates,
                                     String conclusion) { }

    public record MarketMetric(String metricName, double value, String unit, String referencePeriod,
                               String sourceTitle, String sourceUrl) { }

    public record GrowthCalculation(String name, double value, String unit, String formula) { }

    public record CompetitorProduct(String company, String model, Long price, String priceScope,
                                    List<String> features, String sourceUrl, String status) { }

    public record PriceSummary(int pricedProductCount, long minimum, long median, long maximum, String unit) { }

    public record DifferentiationRow(String conceptFeature, int supportedProductCount, int comparedProductCount,
                                     double marketPresenceRate, String verdict) { }

    public record MarketAnalysis(
        String summary,
        long tam,
        double cagr,
        String targetSegment,
        List<String> trends,
        List<String> competitors,
        List<String> risks,
        List<String> evidence
    ) { }

    public record BmAnalysis(
        List<ConceptOption> concepts,
        ConceptOption selectedConcept,
        BusinessModelCanvas canvas,
        int validationScore,
        String decision,
        List<String> financialHandoffNotes
    ) { }

    public record ConceptOption(String id, String name, String positioning, String revenueModel, int score) { }

    public record FinancialInputSource(String field, String label, String source, String note) { }

    public record BusinessModelCanvas(
        List<String> partners,
        List<String> activities,
        List<String> resources,
        List<String> valuePropositions,
        List<String> customerRelationships,
        List<String> channels,
        List<String> customerSegments,
        List<String> costStructure,
        List<String> revenueStreams
    ) { }
}
