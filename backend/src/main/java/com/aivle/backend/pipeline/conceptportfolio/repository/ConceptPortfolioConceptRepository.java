package com.aivle.backend.pipeline.conceptportfolio.repository;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptPortfolioConceptRepository extends JpaRepository<ConceptPortfolioConcept, String> {
    List<ConceptPortfolioConcept> findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderByDisplayOrder(
        String runId, Long projectId);
    long countByRunIdAndSelectableTrueAndDeletedAtIsNull(String runId);
    long countByRunIdAndDeletedAtIsNull(String runId);
}
