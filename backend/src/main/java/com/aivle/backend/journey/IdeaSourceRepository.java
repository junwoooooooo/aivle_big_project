package com.aivle.backend.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaSourceRepository extends JpaRepository<IdeaSource, Long> {
    @EntityGraph(attributePaths = "project")
    Optional<IdeaSource> findByIdAndProjectIdAndDeletedAtIsNull(Long id, Long projectId);

    @EntityGraph(attributePaths = "project")
    Optional<IdeaSource> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);

    default Optional<IdeaSource> findCurrent(Long projectId) {
        return findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId);
    }
}
