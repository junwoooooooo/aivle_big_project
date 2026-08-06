package com.aivle.backend.analysis.feasibility.application;

import com.aivle.backend.integration.ai.feasibility.FeasibilityAnalysisAiRequest;

public record FeasibilityJobContext(
    FeasibilityAnalysisAiRequest request, String snapshotJson, String inputHash
) {}
