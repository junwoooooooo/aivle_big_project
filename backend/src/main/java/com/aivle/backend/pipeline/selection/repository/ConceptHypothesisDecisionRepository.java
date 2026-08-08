package com.aivle.backend.pipeline.selection.repository;

import com.aivle.backend.pipeline.selection.domain.ConceptHypothesisDecision;
import com.aivle.backend.pipeline.selection.domain.HypothesisType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptHypothesisDecisionRepository extends JpaRepository<ConceptHypothesisDecision, String> {
    List<ConceptHypothesisDecision> findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(Long selectionId);
    Optional<ConceptHypothesisDecision> findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
        Long selectionId, HypothesisType hypothesisType);
}
