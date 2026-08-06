package com.aivle.backend.journey;

import java.util.Optional;
import com.aivle.backend.taskrun.domain.TaskRunState;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface IdeaInterpretationRunRepository extends JpaRepository<IdeaInterpretationRun, Long> {
    @EntityGraph(attributePaths = {"project", "source", "taskRun"})
    Optional<IdeaInterpretationRun> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);

    default Optional<IdeaInterpretationRun> findCurrent(Long projectId) {
        return findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId);
    }

    @EntityGraph(attributePaths = {"project", "source", "taskRun"})
    Optional<IdeaInterpretationRun> findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        Long projectId, Long sourceId, IdeaInterpretationRun.State state);

    @EntityGraph(attributePaths = {"project", "source", "taskRun"})
    Optional<IdeaInterpretationRun> findTopByTaskRunStateAndTaskRunLastRetryIdempotencyKeyIsNotNullAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
        TaskRunState taskRunState);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"project", "source", "taskRun"})
    @Query("select r from IdeaInterpretationRun r where r.id=:id and r.deletedAt is null")
    Optional<IdeaInterpretationRun> findLockedById(@Param("id") Long id);
}
