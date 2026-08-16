package com.aivle.backend.pipeline.launchreadiness.repository;

import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaunchReadinessInputSnapshotRepository extends JpaRepository<LaunchReadinessInputSnapshot, String> {
    Optional<LaunchReadinessInputSnapshot> findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(Long projectId, ModuleType moduleType);
    Optional<LaunchReadinessInputSnapshot> findByIdAndProjectIdAndDeletedAtIsNull(String id, Long projectId);
}
