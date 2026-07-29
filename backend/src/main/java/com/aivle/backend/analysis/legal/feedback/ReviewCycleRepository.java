package com.aivle.backend.analysis.legal.feedback;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, Long> {
    /** 활성(미발행) 사이클 — 프로젝트당 최대 1개를 서비스 계층에서 보장한다. */
    @EntityGraph(attributePaths = {"project", "currentPlan"})
    Optional<ReviewCycle> findTopByProjectIdAndStatusNotAndDeletedAtIsNullOrderByIdDesc(
        Long projectId, ReviewCycleStatus status);

    @EntityGraph(attributePaths = {"project", "currentPlan"})
    Optional<ReviewCycle> findByIdAndProjectIdAndDeletedAtIsNull(Long id, Long projectId);
}
