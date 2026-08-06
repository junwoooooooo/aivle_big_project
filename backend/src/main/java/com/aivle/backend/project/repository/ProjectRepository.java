package com.aivle.backend.project.repository;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {
    List<Project> findAllByOwnerIdAndDeletedAtIsNull(Long ownerId);
    List<Project> findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long ownerId);
    @EntityGraph(attributePaths = {"owner"})
    Optional<Project> findByIdAndDeletedAtIsNull(Long id);
    Optional<Project> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);
    long countByOwnerIdAndDeletedAtIsNull(Long ownerId);
    long countByStatusAndDeletedAtIsNull(com.aivle.backend.common.entity.ProjectStatus status);
    long countByDeletedAtIsNull();

    @Query("""
        select count(p)
        from Project p
        where p.deletedAt is null
          and p.owner.deletedAt is null
        """)
    long countAdminVisible();

    @Query("""
        select count(p)
        from Project p
        where p.deletedAt is null
          and p.owner.deletedAt is null
          and p.status = :status
        """)
    long countAdminVisibleByStatus(
        @Param("status") com.aivle.backend.common.entity.ProjectStatus status
    );

    @Query("""
        select count(p)
        from Project p
        where p.deletedAt is null
          and p.owner.deletedAt is null
          and p.status in :statuses
        """)
    long countAdminVisibleByStatusIn(
        @Param("statuses") Collection<com.aivle.backend.common.entity.ProjectStatus> statuses
    );

    @Query("""
        select count(p)
        from Project p
        where p.deletedAt is null
          and p.owner.deletedAt is null
          and p.createdAt >= :createdAt
        """)
    long countAdminVisibleCreatedSince(@Param("createdAt") LocalDateTime createdAt);

    @Override
    @EntityGraph(attributePaths = {"owner"})
    Page<Project> findAll(Specification<Project> specification, Pageable pageable);

    @Query("select p from Project p join fetch p.owner where p.deletedAt is null")
    Page<Project> findAllActiveForAdmin(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p
        from Project p
        join fetch p.owner
        where p.id = :projectId
          and p.deletedAt is null
        """)
    Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);
}
