package com.aivle.backend.pipeline.marketinterview;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketInterviewRunRepository extends JpaRepository<MarketInterviewRun, Long> {
    @EntityGraph(attributePaths = {"project", "taskRun"})
    Optional<MarketInterviewRun> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);
    @EntityGraph(attributePaths = {"project", "taskRun"})
    Optional<MarketInterviewRun> findByTaskRunIdAndDeletedAtIsNull(String taskRunId);
}
