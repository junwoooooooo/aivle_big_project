package com.aivle.backend.pipeline.concept.repository;

import com.aivle.backend.pipeline.concept.domain.Concept;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptRepository extends JpaRepository<Concept, String> {
    List<Concept> findAllByRunIdAndProjectIdAndPublishedTrueAndDeletedAtIsNullOrderBySlotSlotNumber(String runId, Long projectId);
}
