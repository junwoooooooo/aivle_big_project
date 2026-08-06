package com.aivle.backend.pipeline.legal.repository;

import com.aivle.backend.pipeline.legal.domain.ConceptLegalEvidenceLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConceptLegalEvidenceLinkRepository extends JpaRepository<ConceptLegalEvidenceLink, Long> {
    List<ConceptLegalEvidenceLink> findAllByAssessmentIdAndProjectIdAndDeletedAtIsNull(String assessmentId, Long projectId);
}
