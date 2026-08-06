package com.aivle.backend.pipeline.marketing.application;

import java.util.List;

public record MarketingSourceSnapshot(
    String conceptName,
    String targetSegment,
    String problem,
    String valueProposition,
    String positioning,
    List<String> keyFeatures,
    String pricing,
    List<String> channels,
    List<String> competitorDifferentiators,
    List<String> allowedClaims,
    List<String> prohibitedClaims,
    List<String> requiredDisclosures,
    String sourceSnapshotHash
) { }
