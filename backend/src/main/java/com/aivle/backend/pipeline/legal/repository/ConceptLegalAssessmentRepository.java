package com.aivle.backend.pipeline.legal.repository;

import com.aivle.backend.pipeline.legal.domain.ConceptLegalAssessment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptLegalAssessmentRepository extends JpaRepository<ConceptLegalAssessment, String> {
    Optional<ConceptLegalAssessment> findByConceptIdAndProjectIdAndDeletedAtIsNull(String conceptId, Long projectId);
}
