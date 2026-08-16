package com.aivle.backend.pipeline.refinement;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConceptRefinementRoundRepository extends JpaRepository<ConceptRefinementRound, Long> {
    Optional<ConceptRefinementRound> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);
    Optional<ConceptRefinementRound> findTopByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberDescIdDesc(
        Long projectId, String businessValidationSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ConceptRefinementRound r where r.taskRunId=:taskRunId and r.deletedAt is null")
    Optional<ConceptRefinementRound> findByTaskRunIdForUpdate(@Param("taskRunId") String taskRunId);
}
