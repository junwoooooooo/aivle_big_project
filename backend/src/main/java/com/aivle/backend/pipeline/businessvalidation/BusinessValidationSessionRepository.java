package com.aivle.backend.pipeline.businessvalidation;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessValidationSessionRepository
        extends JpaRepository<BusinessValidationSession, String> {

    @EntityGraph(attributePaths = {"project", "project.owner"})
    Optional<BusinessValidationSession> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        Long projectId);

    @EntityGraph(attributePaths = {"project", "project.owner"})
    Optional<BusinessValidationSession> findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(
        Long projectId, String commandIdempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select session from BusinessValidationSession session
        join fetch session.project project
        join fetch project.owner
        where session.id = :id and session.deletedAt is null
        """)
    Optional<BusinessValidationSession> findByIdForUpdate(@Param("id") String id);

    @Query("""
        select session.id from BusinessValidationSession session
        where session.deletedAt is null and session.state in :states
        """)
    List<String> findActiveIds(@Param("states") Collection<BusinessValidationSession.State> states);
}
