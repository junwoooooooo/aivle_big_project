package com.aivle.backend.job.repository;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.common.entity.JobType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.aivle.backend.common.entity.JobStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
    Optional<AnalysisJob> findTopByProjectIdAndJobTypeOrderByCreatedAtDesc(Long projectId, JobType jobType);
    boolean existsByProjectIdAndJobTypeAndStatusInAndDeletedAtIsNull(
        Long projectId, JobType jobType, List<JobStatus> statuses);

    @EntityGraph(attributePaths = {"sourceDocumentVersion", "sourceDocumentVersion.document"})
    Optional<AnalysisJob> findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
        Long projectId,
        JobType jobType,
        String idempotencyKey
    );

    @EntityGraph(attributePaths = {"project"})
    Optional<AnalysisJob> findByIdAndProjectOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    @EntityGraph(attributePaths = {"project", "rerunOfJob"})
    Optional<AnalysisJob>
        findByIdAndProjectIdAndProjectOwnerIdAndJobTypeAndDeletedAtIsNull(
            Long id,
            Long projectId,
            Long ownerId,
            JobType jobType
        );

    @EntityGraph(attributePaths = {"project", "sourceDocumentVersion"})
    Optional<AnalysisJob>
        findTopByProjectIdAndProjectOwnerIdAndJobTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long projectId,
            Long ownerId,
            JobType jobType
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select j
        from AnalysisJob j
        where j.jobType in :jobTypes
          and j.status = :status
          and (
            (j.jobType = com.aivle.backend.common.entity.JobType.DOCUMENT_PARSE
              and j.sourceDocumentVersion is not null)
            or
            (j.jobType = com.aivle.backend.common.entity.JobType.LEGAL_REVIEW
              and j.sourceStructuredPlan is not null)
            or
            (j.jobType = com.aivle.backend.common.entity.JobType.FEASIBILITY_ANALYSIS
              and j.sourceStructuredPlan is not null
              and j.sourceLegalReview is not null)
            or
            (j.jobType = com.aivle.backend.common.entity.JobType.PERSONA_RECOMMENDATION
              and j.sourceStructuredPlan is not null
              and j.sourceFeasibilityAssessment is not null)
            or
            j.jobType = com.aivle.backend.common.entity.JobType.SYSTEM_SMOKE_TEST
            or
            j.jobType = com.aivle.backend.common.entity.JobType.SYSTEM_ARTIFACT_SMOKE_TEST
            or
            j.jobType = com.aivle.backend.common.entity.JobType.MARKETING_GENERATION
          )
          and j.deletedAt is null
          and (j.nextAttemptAt is null or j.nextAttemptAt <= :now)
        order by j.createdAt, j.id
        """)
    List<AnalysisJob> findClaimCandidates(
        @Param("jobTypes") List<JobType> jobTypes,
        @Param("status") JobStatus status,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
        "project",
        "sourceDocumentVersion",
        "sourceDocumentVersion.document",
        "sourceDocumentVersion.storedFile",
        "sourceStructuredPlan",
        "sourceStructuredPlan.sourceDocumentVersion",
        "sourceLegalReview",
        "sourceFeasibilityAssessment"
    })
    @Query("""
        select j
        from AnalysisJob j
        where j.id = :jobId
          and j.deletedAt is null
        """)
    Optional<AnalysisJob> findByIdForUpdate(@Param("jobId") Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select j
        from AnalysisJob j
        where j.jobType = :jobType
          and j.status = :status
          and j.deletedAt is null
        order by coalesce(j.heartbeatAt, j.claimedAt), j.id
        """)
    List<AnalysisJob> findRecoveryCandidates(
        @Param("jobType") JobType jobType,
        @Param("status") JobStatus status,
        Pageable pageable
    );
}
