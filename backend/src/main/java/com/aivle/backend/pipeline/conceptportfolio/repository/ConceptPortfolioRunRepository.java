package com.aivle.backend.pipeline.conceptportfolio.repository;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRun;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConceptPortfolioRunRepository extends JpaRepository<ConceptPortfolioRun, String> {
    Optional<ConceptPortfolioRun> findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(
        Long projectId, String idempotencyKey);

    Optional<ConceptPortfolioRun> findFirstByProjectIdAndProductStatusInAndDeletedAtIsNull(
        Long projectId, Collection<ConceptPortfolioRunStatus> statuses);

    @Query("""
        select r from ConceptPortfolioRun r
        join fetch r.project p
        join fetch r.sourceIdeaBrief b
        where r.id = :runId and p.id = :projectId and p.owner.id = :ownerId
          and r.deletedAt is null and p.deletedAt is null
        """)
    Optional<ConceptPortfolioRun> findOwned(@Param("ownerId") Long ownerId,
        @Param("projectId") Long projectId, @Param("runId") String runId);

    @Query("""
        select r from ConceptPortfolioRun r
        join fetch r.project p
        join fetch r.sourceIdeaBrief b
        where p.id = :projectId and p.owner.id = :ownerId and r.isCurrent = true
          and r.deletedAt is null and p.deletedAt is null
        """)
    Optional<ConceptPortfolioRun> findCurrentOwned(@Param("ownerId") Long ownerId,
        @Param("projectId") Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from ConceptPortfolioRun r
        join fetch r.project p
        join fetch r.sourceIdeaBrief b
        where r.id = :runId and r.deletedAt is null
        """)
    Optional<ConceptPortfolioRun> findLocked(@Param("runId") String runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from ConceptPortfolioRun r
        where r.project.id = :projectId and r.isCurrent = true and r.deletedAt is null
        """)
    Optional<ConceptPortfolioRun> findCurrentForUpdate(@Param("projectId") Long projectId);
}
