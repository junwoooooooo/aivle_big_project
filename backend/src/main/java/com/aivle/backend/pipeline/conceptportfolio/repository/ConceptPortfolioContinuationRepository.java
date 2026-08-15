package com.aivle.backend.pipeline.conceptportfolio.repository;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioContinuation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptPortfolioContinuationRepository
        extends JpaRepository<ConceptPortfolioContinuation, String> {
    Optional<ConceptPortfolioContinuation> findByRunIdAndDeletedAtIsNull(String runId);
}
