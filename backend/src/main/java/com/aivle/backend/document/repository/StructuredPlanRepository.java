package com.aivle.backend.document.repository;
import com.aivle.backend.document.entity.StructuredPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import com.aivle.backend.common.entity.StructuredPlanStatus;
public interface StructuredPlanRepository extends JpaRepository<StructuredPlan, Long> {
    Optional<StructuredPlan> findTopByProjectIdOrderByVersionNumberDesc(Long projectId);
    boolean existsBySourceDocumentVersionIdAndDeletedAtIsNull(Long sourceDocumentVersionId);
    /** 파생 버전이 문서 버전을 공유하므로 다건일 수 있다 — 업로드 멱등 체크에는 origin 스코프 변형을 쓸 것. */
    Optional<StructuredPlan> findBySourceDocumentVersionIdAndDeletedAtIsNull(Long sourceDocumentVersionId);
    Optional<StructuredPlan> findBySourceDocumentVersionIdAndOriginAndDeletedAtIsNull(
        Long sourceDocumentVersionId, com.aivle.backend.document.entity.PlanOrigin origin);
    java.util.List<StructuredPlan> findAllByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(Long projectId);
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
