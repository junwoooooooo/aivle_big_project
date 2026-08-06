package com.aivle.backend.persona.recommendation.application;

import com.aivle.backend.integration.ai.persona.PersonaRecommendationAiRequest;

public record PersonaJobContext(
    PersonaRecommendationAiRequest request,
    String snapshotJson,
    String inputHash
) {}
