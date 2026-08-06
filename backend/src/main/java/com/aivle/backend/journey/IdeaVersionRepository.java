package com.aivle.backend.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaVersionRepository extends JpaRepository<IdeaVersion, Long> {
    @EntityGraph(attributePaths = {"project", "source"})
    Optional<IdeaVersion> findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(Long projectId);

    default Optional<IdeaVersion> findCurrent(Long projectId) {
        return findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId);
    }

    @EntityGraph(attributePaths = {"project", "source"})
    Optional<IdeaVersion> findTopBySourceIdAndDeletedAtIsNullOrderByVersionNumberDesc(Long sourceId);

    long countByProjectIdAndDeletedAtIsNull(Long projectId);
}
