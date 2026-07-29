package com.aivle.backend.analysis.legal.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PublicationRepository extends JpaRepository<Publication, Long> {
    Optional<Publication> findByReviewCycleIdAndDeletedAtIsNull(Long reviewCycleId);

    Optional<Publication> findTopByProjectIdAndDeletedAtIsNullOrderByPublishedAtDesc(Long projectId);
}
