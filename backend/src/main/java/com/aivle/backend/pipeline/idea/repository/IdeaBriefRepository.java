package com.aivle.backend.pipeline.idea.repository;

import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdeaBriefRepository extends JpaRepository<IdeaBrief, String> {
    Optional<IdeaBrief> findByIdAndProjectIdAndDeletedAtIsNull(String id, Long projectId);
    @Query("""
        select b from IdeaBrief b
        join fetch b.project p
        where p.id = :projectId and p.owner.id = :ownerId and p.deletedAt is null and b.deletedAt is null
        order by b.briefSequence desc
        limit 1
        """)
    Optional<IdeaBrief> findCurrentOwned(@Param("ownerId") Long ownerId, @Param("projectId") Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select b from IdeaBrief b
        join fetch b.project p
        where p.id = :projectId and p.owner.id = :ownerId and p.deletedAt is null and b.deletedAt is null
        order by b.briefSequence desc
        limit 1
        """)
    Optional<IdeaBrief> findCurrentOwnedForUpdate(@Param("ownerId") Long ownerId, @Param("projectId") Long projectId);
}
