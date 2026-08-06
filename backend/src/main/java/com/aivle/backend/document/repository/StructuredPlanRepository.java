package com.aivle.backend.document.repository;
import com.aivle.backend.document.entity.StructuredPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import com.aivle.backend.common.entity.StructuredPlanStatus;
public interface StructuredPlanRepository extends JpaRepository<StructuredPlan, Long> {
    Optional<StructuredPlan> findTopByProjectIdOrderByVersionNumberDesc(Long projectId);
    boolean existsBySourceDocumentVersionIdAndDeletedAtIsNull(Long sourceDocumentVersionId);
    Optional<StructuredPlan> findBySourceDocumentVersionIdAndDeletedAtIsNull(Long sourceDocumentVersionId);
    @EntityGraph(attributePaths = {"project", "sourceDocumentVersion", "confirmedBy"})
    Optional<StructuredPlan> findByIdAndProjectIdAndDeletedAtIsNull(
        Long id,
        Long projectId
    );

    @EntityGraph(attributePaths = {"project", "sourceDocumentVersion"})
    Optional<StructuredPlan> findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(Long projectId);
    @EntityGraph(attributePaths = {"project", "sourceDocumentVersion"})
    Optional<StructuredPlan> findTopByProjectIdAndStatusAndCompletionRateAndDeletedAtIsNullOrderByVersionNumberDesc(
        Long projectId, StructuredPlanStatus status, Integer completionRate);
}
