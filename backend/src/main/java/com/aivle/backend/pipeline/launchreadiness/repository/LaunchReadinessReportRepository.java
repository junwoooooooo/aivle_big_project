package com.aivle.backend.pipeline.launchreadiness.repository;

import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessReport;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaunchReadinessReportRepository extends JpaRepository<LaunchReadinessReport, String> {
    Optional<LaunchReadinessReport> findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(Long projectId, ModuleType moduleType);
    Optional<LaunchReadinessReport> findByTaskRunIdAndDeletedAtIsNull(String taskRunId);
}
