package com.aivle.backend.pipeline.market;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketResearchRunRepository extends JpaRepository<MarketResearchRun, Long> {

    /** 그 모드의 최신 실행. 화면의 「현재」가 이것이다. */
    @EntityGraph(attributePaths = {"project", "taskRun", "sourceRun"})
    Optional<MarketResearchRun> findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        Long projectId, MarketResearchRun.Kind kind);

    @EntityGraph(attributePaths = {"project", "taskRun", "sourceRun"})
    Optional<MarketResearchRun> findByTaskRunIdAndDeletedAtIsNull(String taskRunId);
}
