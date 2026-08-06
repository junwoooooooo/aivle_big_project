package com.aivle.backend.journey.boundary;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegulatoryBoundaryVersionRepository extends JpaRepository<RegulatoryBoundaryVersion, Long> {
    @EntityGraph(attributePaths = {"project", "run", "briefVersion"})
    Optional<RegulatoryBoundaryVersion> findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(Long projectId);

    @EntityGraph(attributePaths = {"project", "run", "briefVersion"})
    Optional<RegulatoryBoundaryVersion> findByIdAndProjectIdAndDeletedAtIsNull(Long id, Long projectId);
    List<RegulatoryBoundaryVersion> findByProjectIdAndStatusNotAndDeletedAtIsNull(
        Long projectId, RegulatoryBoundaryVersion.Status status);
    long countByProjectIdAndDeletedAtIsNull(Long projectId);
    Optional<RegulatoryBoundaryVersion> findByRunIdAndDeletedAtIsNull(Long runId);
}
