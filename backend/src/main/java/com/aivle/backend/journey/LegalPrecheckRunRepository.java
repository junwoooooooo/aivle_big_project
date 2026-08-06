package com.aivle.backend.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalPrecheckRunRepository extends JpaRepository<LegalPrecheckRun, Long> {
    @EntityGraph(attributePaths = {"project", "ideaOriginVersion", "taskRun"})
    Optional<LegalPrecheckRun> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);
    @EntityGraph(attributePaths = {"project", "ideaOriginVersion", "taskRun"})
    Optional<LegalPrecheckRun> findTopByProjectIdAndIdeaOriginVersionIdAndInputSnapshotHashAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        Long projectId, Long originId, String inputHash);
}
