package com.aivle.backend.integration.ai.legal;

import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.analysis.legal.entity.ReviewMode;
import java.util.List;

public record LegalReviewAiRequest(
    Long projectId,
    Long structuredPlanId,
    Long sourceDocumentVersionId,
    String promptVersion,
    String promptText,
    List<Section> sections,
    ReviewMode mode,
    List<LegalCategory> rerunCategories,
    List<ConfirmedFactPayload> confirmedFacts
) {
    public LegalReviewAiRequest {
        if (mode == null) {
            mode = ReviewMode.FULL;
        }
        rerunCategories = rerunCategories == null ? List.of() : List.copyOf(rerunCategories);
        confirmedFacts = confirmedFacts == null ? List.of() : List.copyOf(confirmedFacts);
    }

    /** FULL 검토용 하위호환 생성자. */
    public LegalReviewAiRequest(
        Long projectId,
        Long structuredPlanId,
        Long sourceDocumentVersionId,
        String promptVersion,
        String promptText,
        List<Section> sections
    ) {
        this(projectId, structuredPlanId, sourceDocumentVersionId, promptVersion, promptText,
            sections, ReviewMode.FULL, List.of(), List.of());
    }

    public record Section(String code, String title, String content, String evidenceJson) {}

    public record ConfirmedFactPayload(String key, String value, String source, String answeredAt) {}
}
