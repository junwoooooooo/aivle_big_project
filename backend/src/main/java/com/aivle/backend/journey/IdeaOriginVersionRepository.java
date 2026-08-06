package com.aivle.backend.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaOriginVersionRepository extends JpaRepository<IdeaOriginVersion, Long> {
    @EntityGraph(attributePaths = {"project", "source", "sourceIdeaVersion", "basedOnOriginVersion"})
    Optional<IdeaOriginVersion> findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
        Long projectId, Long sourceId, IdeaOriginVersion.State state);

    @EntityGraph(attributePaths = {"project", "source", "sourceIdeaVersion", "basedOnOriginVersion"})
    Optional<IdeaOriginVersion> findTopByProjectIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
        Long projectId, IdeaOriginVersion.State state);

    long countByProjectIdAndDeletedAtIsNull(Long projectId);
}
