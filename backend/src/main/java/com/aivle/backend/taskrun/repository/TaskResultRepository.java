package com.aivle.backend.taskrun.repository;

import com.aivle.backend.taskrun.domain.TaskResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskResultRepository extends JpaRepository<TaskResult, String> {
    List<TaskResult> findByTaskRunId(String taskRunId);
}
