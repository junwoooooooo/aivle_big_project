package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.analysis.legal.entity.ReviewMode;
import com.aivle.backend.integration.ai.legal.LegalReviewAiRequest;
import java.util.List;
import java.util.Set;

public record LegalReviewJobContext(
    Long projectId, Long planId, Long sourceDocumentVersionId,
    List<LegalReviewAiRequest.Section> sections, String snapshotJson,
    ReviewMode mode, Long cycleId, Long parentReviewId,
    List<String> changedSections,
    Set<LegalCategory> rerunCategories,
    Set<LegalCategory> carriedCategories,
    List<LegalReviewAiRequest.ConfirmedFactPayload> confirmedFacts
) {}
