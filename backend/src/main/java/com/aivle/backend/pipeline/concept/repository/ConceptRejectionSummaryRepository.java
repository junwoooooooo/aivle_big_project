package com.aivle.backend.pipeline.concept.repository;

import com.aivle.backend.pipeline.concept.domain.ConceptRejectionSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptRejectionSummaryRepository extends JpaRepository<ConceptRejectionSummary, Long> {
    long countBySlotRunIdAndDeletedAtIsNull(String runId);
}
