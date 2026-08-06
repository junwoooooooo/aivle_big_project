package com.aivle.backend.analysis.legal.repository;
import com.aivle.backend.analysis.legal.entity.LegalReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
public interface LegalReviewRepository extends JpaRepository<LegalReview, Long> {
    boolean existsByStructuredPlanIdAndPromptVersionAndDeletedAtIsNull(Long planId, String promptVersion);
    Optional<LegalReview> findByStructuredPlanIdAndPromptVersionAndDeletedAtIsNull(
        Long planId, String promptVersion);
    @EntityGraph(attributePaths = {"project", "structuredPlan", "sourceDocumentVersion"})
    Optional<LegalReview> findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        Long projectId, Long ownerId);
    @EntityGraph(attributePaths = {"project", "structuredPlan", "sourceDocumentVersion"})
    Optional<LegalReview>
        findTopByProjectIdAndStructuredPlanIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long projectId, Long structuredPlanId);
}
