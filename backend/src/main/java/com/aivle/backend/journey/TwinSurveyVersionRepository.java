package com.aivle.backend.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TwinSurveyVersionRepository extends JpaRepository<TwinSurveyVersion, Long> {

    @EntityGraph(attributePaths = {"project", "sourceRun", "sourceRun.taskRun"})
    Optional<TwinSurveyVersion> findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(Long projectId);

    /** 멱등의 열쇠 — 같은 실행에 두 번 물질화하지 않는다. */
    Optional<TwinSurveyVersion> findBySourceRunIdAndDeletedAtIsNull(Long sourceRunId);

    long countByProjectIdAndDeletedAtIsNull(Long projectId);
}
