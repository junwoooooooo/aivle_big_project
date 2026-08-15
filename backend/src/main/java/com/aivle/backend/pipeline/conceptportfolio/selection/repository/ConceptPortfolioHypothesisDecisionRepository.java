package com.aivle.backend.pipeline.conceptportfolio.selection.repository;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptPortfolioHypothesisDecisionRepository
        extends JpaRepository<ConceptPortfolioHypothesisDecision, String> {
    List<ConceptPortfolioHypothesisDecision> findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(Long selectionId);
    Optional<ConceptPortfolioHypothesisDecision> findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
        Long selectionId, PortfolioHypothesisType type);
}
