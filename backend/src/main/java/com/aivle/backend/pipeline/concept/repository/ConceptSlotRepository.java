package com.aivle.backend.pipeline.concept.repository;

import com.aivle.backend.pipeline.concept.domain.ConceptSlot;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptSlotRepository extends JpaRepository<ConceptSlot, String> {
    List<ConceptSlot> findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(String runId, Long projectId);
    long countByRunIdAndStatusAndDeletedAtIsNull(String runId, ConceptSlotStatus status);
}
