package com.aivle.backend.pipeline.concept.repository;

import com.aivle.backend.pipeline.concept.domain.Concept;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptRepository extends JpaRepository<Concept, String> {
    List<Concept> findAllByRunIdAndProjectIdAndPublishedTrueAndDeletedAtIsNullOrderBySlotSlotNumber(String runId, Long projectId);
    List<Concept> findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotSlotNumber(String runId, Long projectId);
    Optional<Concept> findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull(String id, Long projectId);
}
