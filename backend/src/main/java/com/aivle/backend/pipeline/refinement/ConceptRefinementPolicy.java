package com.aivle.backend.pipeline.refinement;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-owned identity for the deterministic refinement gate. */
public final class ConceptRefinementPolicy {
    public static final String VERSION = "REFINEMENT_POLICY_V1";
    public static final int MAX_PROPOSALS = 6;
    public static final int MAX_ROUNDS = 3;
    public static final int MAX_ATTEMPTS_PER_ROUND = 3;
    public static final double PRICE_TOLERANCE = 0.30;
    public static final int LIST_CHANGE_ALLOWANCE = 1;

    public static final Set<String> FROZEN_FIELDS = Set.of(
        "sellerRole", "providerRole", "intermediaryRole", "transactionFlow", "paymentFlow",
        "personalDataUsage", "physicalActivities", "partnerRequirements",
        "qualificationRequirements", "advertisingClaims", "conceptName",
        "conceptDefinition", "coreValue", "operatingModel", "platformRole");

    public static final Map<String, String> REFINABLE_FIELDS = Map.of(
        "price", "PRICE_BAND",
        "channels", "LIST_ADD_OR_SWAP",
        "differentiators", "LIST_ADD_OR_SWAP",
        "targetRegion", "NARROW_ONLY",
        "targetUsers", "NARROW_ONLY",
        "featureSet", "SUBSET_ONLY",
        "revenueModel", "STRUCTURE_ONLY");

    public static final List<String> FREE_WITH_EVIDENCE_FIELDS = List.of(
        "preMarketSomShare", "preMarketSom");
    public static final List<String> FREE_BM_FIELDS = List.of(
        "keyActivities", "keyResources", "keyPartners", "customerRelationships");

    private ConceptRefinementPolicy() { }
}
