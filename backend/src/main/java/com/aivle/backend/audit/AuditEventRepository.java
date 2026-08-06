package com.aivle.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {
    List<AuditEvent> findAllByActorUserIdOrderByOccurredAtDesc(Long actorUserId);

    List<AuditEvent> findAllByProjectIdOrderByOccurredAtDesc(Long projectId);
    Page<AuditEvent> findAllByEventTypeContainingIgnoreCaseOrderByOccurredAtDesc(String eventType, Pageable pageable);
    Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"actor"})
    Page<AuditEvent> findAll(Specification<AuditEvent> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"actor"})
    @Query("select event from AuditEvent event left join fetch event.actor where event.id = :id")
    Optional<AuditEvent> findWithActorById(@Param("id") Long id);
}
