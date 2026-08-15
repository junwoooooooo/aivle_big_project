package com.aivle.backend.pipeline.conceptportfolio.selection.repository;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioDeltaLegalReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptPortfolioDeltaLegalReviewRepository extends JpaRepository<ConceptPortfolioDeltaLegalReview, String> {
    List<ConceptPortfolioDeltaLegalReview> findAllBySelectionIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long selectionId);
    Optional<ConceptPortfolioDeltaLegalReview>
        findFirstBySelectionIdAndHypothesisRevisionAndApprovedTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long selectionId, int hypothesisRevision);
}
