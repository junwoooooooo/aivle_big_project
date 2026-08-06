package com.aivle.backend.analysis.feasibility.repository;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityAssessment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FeasibilityAssessmentRepository extends JpaRepository<FeasibilityAssessment, Long> {
    Optional<FeasibilityAssessment>
        findByStructuredPlanIdAndLegalReviewIdAndPromptVersionAndCatalogVersionAndDeletedAtIsNull(
            Long planId, Long legalReviewId, String promptVersion, String catalogVersion);

    @EntityGraph(attributePaths = {
        "project", "structuredPlan", "sourceDocumentVersion", "legalReview", "analysisJob"
    })
    Optional<FeasibilityAssessment>
        findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long projectId, Long ownerId);
}
