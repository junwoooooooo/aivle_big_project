package com.aivle.backend.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface LegalReviewRunRepository extends JpaRepository<LegalReviewRun, Long> {
    @EntityGraph(attributePaths = {"project", "ideaVersion", "taskRun"})
    Optional<LegalReviewRun> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);

    default Optional<LegalReviewRun> findCurrent(Long projectId) {
        return findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId);
    }

    @EntityGraph(attributePaths = {"project", "ideaVersion", "taskRun"})
    Optional<LegalReviewRun> findTopByProjectIdAndIdeaVersionIdAndStateAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        Long projectId, Long ideaVersionId, LegalReviewRun.State state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"project", "ideaVersion", "taskRun"})
    @Query("select r from LegalReviewRun r where r.id=:id and r.deletedAt is null")
    Optional<LegalReviewRun> findLockedById(@Param("id") Long id);
}
