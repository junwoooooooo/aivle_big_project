package com.aivle.backend.pipeline.refinement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ConceptRefinementFinalRepository extends JpaRepository<ConceptRefinementFinal,Long> {
    Optional<ConceptRefinementFinal> findByRoundIdAndDeletedAtIsNull(Long roundId);
    Optional<ConceptRefinementFinal> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);
}
