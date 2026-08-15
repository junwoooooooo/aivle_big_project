package com.aivle.backend.jobevent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobEventRepository extends JpaRepository<JobEvent, Long> {
    @EntityGraph(attributePaths = {"project", "taskRun"})
    List<JobEvent> findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
        String jobId, Long projectId, long sequence);

    @EntityGraph(attributePaths = {"project", "taskRun"})
    List<JobEvent> findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
        String jobId, Long projectId, long sequence, Pageable pageable);

    Optional<JobEvent> findTopByJobIdAndProjectIdAndDeletedAtIsNullOrderBySequenceDesc(
        String jobId, Long projectId);

    @EntityGraph(attributePaths = {"project", "taskRun"})
    List<JobEvent> findByProjectIdAndIdGreaterThanAndDeletedAtIsNullOrderById(
        Long projectId, long id, Pageable pageable);

    Optional<JobEvent> findTopByProjectIdAndDeletedAtIsNullOrderByIdDesc(Long projectId);

    @EntityGraph(attributePaths = {"project", "project.owner", "taskRun"})
    Optional<JobEvent> findTopByJobIdAndDeletedAtIsNullOrderBySequenceDesc(String jobId);
}
