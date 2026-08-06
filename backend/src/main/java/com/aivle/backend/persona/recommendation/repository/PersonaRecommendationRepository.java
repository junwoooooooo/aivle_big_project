package com.aivle.backend.persona.recommendation.repository;

import com.aivle.backend.persona.recommendation.entity.PersonaRecommendation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PersonaRecommendationRepository
    extends JpaRepository<PersonaRecommendation, Long> {
    Optional<PersonaRecommendation>
        findByStructuredPlanIdAndFeasibilityAssessmentIdAndPromptVersionAndCatalogVersionAndDeletedAtIsNull(
            Long planId, Long feasibilityAssessmentId, String promptVersion, String catalogVersion);

    @EntityGraph(attributePaths = {
        "project", "analysisJob", "structuredPlan", "feasibilityAssessment",
        "sourceDocumentVersion"
    })
    Optional<PersonaRecommendation>
        findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long projectId, Long ownerId);
}
