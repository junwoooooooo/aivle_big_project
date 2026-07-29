package com.aivle.backend.analysis.legal.feedback;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RevisionRequestRepository extends JpaRepository<RevisionRequest, Long> {
    List<RevisionRequest> findByReviewCycleIdAndDeletedAtIsNullOrderById(Long reviewCycleId);

    long countByReviewCycleIdAndStatusAndResolvedInVersionIsNullAndDeletedAtIsNull(
        Long reviewCycleId, RevisionRequestStatus status);

    @EntityGraph(attributePaths = {"reviewCycle", "raisedInReview"})
    Optional<RevisionRequest> findByIdAndDeletedAtIsNull(Long id);
}
