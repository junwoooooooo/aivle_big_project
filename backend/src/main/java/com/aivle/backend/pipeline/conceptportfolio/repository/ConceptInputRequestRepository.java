package com.aivle.backend.pipeline.conceptportfolio.repository;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptInputRequest;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptInputRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConceptInputRequestRepository extends JpaRepository<ConceptInputRequest, String> {
    Optional<ConceptInputRequest> findByRunIdAndRequestHashAndDeletedAtIsNull(
        String runId, String requestHash);
    long countByRunIdAndStatusInAndDeletedAtIsNull(
        String runId, Collection<ConceptInputRequestStatus> statuses);
    List<ConceptInputRequest> findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
        String runId, Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ConceptInputRequest r join fetch r.run run join fetch r.project "
        + "where r.id=:id and r.deletedAt is null")
    Optional<ConceptInputRequest> findLocked(@Param("id") String id);
}
