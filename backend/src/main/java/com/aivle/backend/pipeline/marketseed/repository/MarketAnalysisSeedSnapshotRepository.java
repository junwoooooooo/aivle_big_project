package com.aivle.backend.pipeline.marketseed.repository;

import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketAnalysisSeedSnapshotRepository extends JpaRepository<MarketAnalysisSeedSnapshot, String> {
    Optional<MarketAnalysisSeedSnapshot> findBySelectionIdAndProjectIdAndDeletedAtIsNull(Long selectionId, Long projectId);
    Optional<MarketAnalysisSeedSnapshot> findFirstByProjectIdAndDeletedAtIsNullOrderByFinalizedAtDesc(Long projectId);
}
