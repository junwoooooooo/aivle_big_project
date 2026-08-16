package com.aivle.backend.pipeline.marketing.repository;

import com.aivle.backend.pipeline.marketing.domain.MarketingSourceSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingSourceSnapshotRepository extends JpaRepository<MarketingSourceSnapshot, String> {
    Optional<MarketingSourceSnapshot> findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(String marketSeedId, Long projectId);
    Optional<MarketingSourceSnapshot> findBySourceMarketSeedSnapshotIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndProjectIdAndDeletedAtIsNull(
        String marketSeedId, Integer selectionRevision, Integer bmPlanRevision, Long projectId);
    Optional<MarketingSourceSnapshot> findFirstByProjectIdAndDeletedAtIsNullOrderByFinalizedAtDesc(Long projectId);
}
