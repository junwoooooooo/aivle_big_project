package com.aivle.backend.persona.catalog.dto;

import java.math.BigDecimal;

public record BaselinePersonaResponse(
    Long id,
    String personaCode,
    String clusterId,
    String displayName,
    String shortName,
    String description,
    String ageGroup,
    String gender,
    Integer sampleSize,
    BigDecimal weightedShare,
    String dataSource,
    String dataVersion,
    String catalogVersion,
    String keyTraitsJson,
    String demographicSummaryJson,
    String evidenceMetricsJson,
    String limitationsJson
) {}
