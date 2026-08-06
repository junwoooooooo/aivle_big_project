package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.integration.ai.legal.LegalReviewAiRequest;
import java.util.List;

public record LegalReviewJobContext(
    Long projectId, Long planId, Long sourceDocumentVersionId,
    List<LegalReviewAiRequest.Section> sections, String snapshotJson
) {}
