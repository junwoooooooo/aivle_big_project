package com.aivle.backend.pipeline.conceptportfolio.repository;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptPortfolioConceptRepository extends JpaRepository<ConceptPortfolioConcept, String> {
    List<ConceptPortfolioConcept> findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderByDisplayOrder(
        String runId, Long projectId);
    long countByRunIdAndSelectableTrueAndDeletedAtIsNull(String runId);
    long countByRunIdAndDeletedAtIsNull(String runId);
    boolean existsByRunIdAndLineageIdAndDeletedAtIsNull(String runId, String lineageId);
    Optional<ConceptPortfolioConcept> findFirstByRunIdAndDeletedAtIsNullOrderByDisplayOrderDesc(
        String runId);
    Optional<ConceptPortfolioConcept> findByIdAndProjectIdAndDeletedAtIsNull(String id, Long projectId);
}
