package com.aivle.backend.integration.ai.legal;

public interface LegalReviewAiClient {
    LegalReviewAiResponse review(LegalReviewAiRequest request);
}
