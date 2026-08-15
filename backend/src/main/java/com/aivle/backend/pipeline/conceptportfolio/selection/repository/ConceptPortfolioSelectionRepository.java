package com.aivle.backend.pipeline.conceptportfolio.selection.repository;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ConceptPortfolioSelectionRepository extends JpaRepository<ConceptPortfolioSelection, Long> {
    Optional<ConceptPortfolioSelection> findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(Long projectId, String key);
    Optional<ConceptPortfolioSelection> findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(Long projectId);
    Optional<ConceptPortfolioSelection> findByIdAndProjectIdAndDeletedAtIsNull(Long id, Long projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ConceptPortfolioSelection s where s.id=:id and s.deletedAt is null")
    Optional<ConceptPortfolioSelection> findLocked(@Param("id") Long id);
}
