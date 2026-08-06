package com.aivle.backend.pipeline.integration.repository;

import com.aivle.backend.pipeline.integration.domain.ModuleRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleRunRepository extends JpaRepository<ModuleRun, String> {
    Optional<ModuleRun> findByHandoffIdAndProjectIdAndDeletedAtIsNull(String handoffId, Long projectId);
    Optional<ModuleRun> findByIdAndProjectIdAndDeletedAtIsNull(String id, Long projectId);
    List<ModuleRun> findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long projectId);
    Optional<ModuleRun> findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long projectId);
    Optional<ModuleRun> findFirstByProjectIdAndModuleAndDeletedAtIsNullOrderByCreatedAtDesc(Long projectId, com.aivle.backend.pipeline.integration.domain.ModuleType module);
}
