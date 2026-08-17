package com.aivle.backend.pipeline.techops.repository;

import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechOpsInputSnapshotRepository extends JpaRepository<TechOpsInputSnapshot, String> {
    Optional<TechOpsInputSnapshot> findByPreparationIdAndProjectIdAndDeletedAtIsNull(String preparationId, Long projectId);
    Optional<TechOpsInputSnapshot> findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(String sourceId, Long projectId);
    Optional<TechOpsInputSnapshot> findFirstByProjectIdAndDeletedAtIsNullOrderByFinalizedAtDesc(Long projectId);
}
