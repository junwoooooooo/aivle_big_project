package com.aivle.backend.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalPrecheckVersionRepository extends JpaRepository<LegalPrecheckVersion, Long> {
    @EntityGraph(attributePaths = {"project", "ideaOriginVersion", "sourceRun", "sourceRun.taskRun"})
    Optional<LegalPrecheckVersion> findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(Long projectId);
    @EntityGraph(attributePaths = {"project", "ideaOriginVersion", "sourceRun", "sourceRun.taskRun"})
    Optional<LegalPrecheckVersion> findBySourceRunIdAndDeletedAtIsNull(Long sourceRunId);
    long countByProjectIdAndDeletedAtIsNull(Long projectId);
}
