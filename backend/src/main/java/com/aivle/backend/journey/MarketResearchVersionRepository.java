package com.aivle.backend.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketResearchVersionRepository extends JpaRepository<MarketResearchVersion, Long> {

    @EntityGraph(attributePaths = {"project", "sourceRun", "sourceRun.taskRun"})
    Optional<MarketResearchVersion> findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
        Long projectId, MarketResearchRun.Kind kind);

    /** 멱등의 열쇠 — 같은 실행에 두 번 물질화하지 않는다. */
    Optional<MarketResearchVersion> findBySourceRunIdAndDeletedAtIsNull(Long sourceRunId);

    long countByProjectIdAndKindAndDeletedAtIsNull(Long projectId, MarketResearchRun.Kind kind);
}
