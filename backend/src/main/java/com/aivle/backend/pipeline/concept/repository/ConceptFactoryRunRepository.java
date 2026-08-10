package com.aivle.backend.pipeline.concept.repository;

import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRun;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConceptFactoryRunRepository extends JpaRepository<ConceptFactoryRun, String> {
    @Query("""
        select r from ConceptFactoryRun r join fetch r.project p
        where p.id = :projectId and p.owner.id = :ownerId and p.deletedAt is null and r.deletedAt is null
        order by r.createdAt desc limit 1
        """)
    Optional<ConceptFactoryRun> findCurrentOwned(@Param("ownerId") Long ownerId, @Param("projectId") Long projectId);

    @Query("""
        select r from ConceptFactoryRun r join fetch r.project p
        where r.id = :runId and p.id = :projectId and p.owner.id = :ownerId
          and p.deletedAt is null and r.deletedAt is null
        """)
    Optional<ConceptFactoryRun> findOwned(@Param("ownerId") Long ownerId, @Param("projectId") Long projectId, @Param("runId") String runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from ConceptFactoryRun r join fetch r.project p
        where r.id = :runId and p.id = :projectId and p.owner.id = :ownerId
          and p.deletedAt is null and r.deletedAt is null
        """)
    Optional<ConceptFactoryRun> findOwnedForUpdate(@Param("ownerId") Long ownerId, @Param("projectId") Long projectId, @Param("runId") String runId);
}
