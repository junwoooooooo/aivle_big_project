package com.aivle.backend.pipeline.marketing.repository;

import com.aivle.backend.pipeline.marketing.domain.MarketingContent;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MarketingContentRepository extends JpaRepository<MarketingContent, String> {
    List<MarketingContent> findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long projectId);
    Optional<MarketingContent> findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long projectId);
    Optional<MarketingContent> findFirstByProjectIdAndMarketingSourceSnapshotIdAndStatusAndDeletedAtIsNullOrderByFinalizedAtDesc(
        Long projectId, String marketingSourceSnapshotId,
        com.aivle.backend.pipeline.marketing.domain.MarketingContentStatus status);
    Optional<MarketingContent> findFirstByProjectIdAndMarketingSourceSnapshotIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
        Long projectId, String marketingSourceSnapshotId,
        com.aivle.backend.pipeline.marketing.domain.MarketingContentStatus status);
    Optional<MarketingContent> findByIdAndProjectIdAndDeletedAtIsNull(String id, Long projectId);
    Optional<MarketingContent> findByTaskRunIdAndDeletedAtIsNull(String taskRunId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MarketingContent c where c.id=:id and c.projectId=:projectId and c.deletedAt is null")
    Optional<MarketingContent> findLocked(@Param("id") String id, @Param("projectId") Long projectId);
}
