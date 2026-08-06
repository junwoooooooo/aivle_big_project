package com.aivle.backend.aitask.repository;

import com.aivle.backend.aitask.entity.AiTaskResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTaskResultRepository
    extends JpaRepository<AiTaskResult, Long> {

    Optional<AiTaskResult>
        findByAnalysisJobIdAndDeletedAtIsNull(Long analysisJobId);
}
