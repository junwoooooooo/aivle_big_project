package com.aivle.backend.analysis.legal.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConfirmedFactRepository extends JpaRepository<ConfirmedFact, Long> {
    List<ConfirmedFact> findByReviewCycleIdAndDeletedAtIsNullOrderByAnsweredAt(Long reviewCycleId);

    List<ConfirmedFact> findByReviewCycleIdAndCreatedInPlanIdAndDeletedAtIsNull(
        Long reviewCycleId, Long createdInPlanId);
}
