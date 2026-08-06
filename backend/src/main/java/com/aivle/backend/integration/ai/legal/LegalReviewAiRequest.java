package com.aivle.backend.integration.ai.legal;

import java.util.List;

public record LegalReviewAiRequest(
    Long projectId,
    Long structuredPlanId,
    Long sourceDocumentVersionId,
    String promptVersion,
    String promptText,
    List<Section> sections
) {
    public record Section(String code, String title, String content, String evidenceJson) {}
}
