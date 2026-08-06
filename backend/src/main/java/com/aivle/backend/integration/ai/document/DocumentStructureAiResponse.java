package com.aivle.backend.integration.ai.document;

import com.aivle.backend.document.structure.AiStructuredPlanResult;

public record DocumentStructureAiResponse(
    AiStructuredPlanResult result,
    String providerRequestId
) {
}
