package com.aivle.backend.taskrun.repository;

import com.aivle.backend.taskrun.domain.TaskAttempt;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import com.aivle.backend.taskrun.domain.TaskAttemptState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.aivle.backend.taskrun.domain.TaskType;

public interface TaskAttemptRepository extends JpaRepository<TaskAttempt, String> {
    Optional<TaskAttempt> findByIdAndTaskRunId(String id, String taskRunId);

    @Query("select a.id from TaskAttempt a where a.state in :states and a.leaseExpiresAt<=:cutoff order by a.leaseExpiresAt, a.id")
    List<String> findExpiredIds(@Param("states") List<TaskAttemptState> states, @Param("cutoff") LocalDateTime cutoff);

    @Query("select a.id from TaskAttempt a where a.taskRun.taskType in :types and a.state in :states and a.leaseExpiresAt<=:cutoff order by a.leaseExpiresAt, a.id")
    List<String> findExpiredIdsByTaskTypes(@Param("types") List<TaskType> types,
        @Param("states") List<TaskAttemptState> states, @Param("cutoff") LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TaskAttempt a join fetch a.taskRun where a.id=:id")
    Optional<TaskAttempt> findLocked(@Param("id") String id);
}
