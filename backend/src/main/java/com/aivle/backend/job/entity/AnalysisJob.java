package com.aivle.backend.job.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.document.entity.DocumentVersion;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.analysis.legal.entity.LegalReview;
import com.aivle.backend.analysis.feasibility.entity.FeasibilityAssessment;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
    name = "analysis_jobs",
    indexes = {
        @Index(name = "idx_job_project_type_status", columnList = "project_id,job_type,status"),
        @Index(name = "idx_job_source_document_version", columnList = "source_document_version_id")
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uk_job_idempotency",
        columnNames = {"project_id", "job_type", "idempotency_key"}
    )
)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisJob extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private JobType jobType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private JobStatus status;
    @Column(nullable = false) private Integer progress;
    @Column(length = 200) private String currentStep;
    @Column(columnDefinition = "TEXT") private String requestJson;
    @Column(length = 100) private String externalRequestId;
    @Column(length = 50) private String resultReferenceType;
    private Long resultReferenceId;
    @Column(length = 100) private String errorCode;
    @Column(length = 500) private String errorMessage;
    @Column(nullable = false) private Integer retryCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @Column(length = 100) private String idempotencyKey;
    @Column(length = 64) private String requestFingerprint;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_version_id")
    private DocumentVersion sourceDocumentVersion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_structured_plan_id")
    private StructuredPlan sourceStructuredPlan;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_legal_review_id")
    private LegalReview sourceLegalReview;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_feasibility_assessment_id")
    private FeasibilityAssessment sourceFeasibilityAssessment;
    @Column(nullable = false) private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime claimedAt;
    @Column(length = 100) private String claimedBy;
    private LocalDateTime heartbeatAt;
    @Column(length = 100) private String lastErrorCode;
    private Boolean retryable;
    @Column(length = 64) private String claimToken;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rerun_of_job_id")
    private AnalysisJob rerunOfJob;

    private AnalysisJob(Project project, JobType jobType, String requestJson) {
        this.project = project;
        this.jobType = jobType;
        this.status = JobStatus.QUEUED;
        this.progress = 0;
        this.requestJson = requestJson;
        this.retryCount = 0;
        this.attemptCount = 0;
    }

    public static AnalysisJob queued(Project project, JobType jobType, String requestJson) {
        return new AnalysisJob(project, jobType, requestJson);
    }

    public static AnalysisJob queuedDocumentParse(
        Project project,
        DocumentVersion sourceDocumentVersion,
        String requestJson,
        String idempotencyKey,
        String requestFingerprint
    ) {
        AnalysisJob job = new AnalysisJob(project, JobType.DOCUMENT_PARSE, requestJson);
        job.sourceDocumentVersion = sourceDocumentVersion;
        job.idempotencyKey = idempotencyKey;
        job.requestFingerprint = requestFingerprint;
        return job;
    }

    public static AnalysisJob queuedLegalReview(
        Project project, StructuredPlan sourcePlan, String requestJson,
        String idempotencyKey, String requestFingerprint
    ) {
        AnalysisJob job = new AnalysisJob(project, JobType.LEGAL_REVIEW, requestJson);
        job.sourceStructuredPlan = sourcePlan;
        job.idempotencyKey = idempotencyKey;
        job.requestFingerprint = requestFingerprint;
        return job;
    }

    public static AnalysisJob queuedFeasibilityAssessment(
        Project project, StructuredPlan sourcePlan, LegalReview sourceLegalReview,
        String requestJson, String idempotencyKey, String requestFingerprint
    ) {
        AnalysisJob job = new AnalysisJob(project, JobType.FEASIBILITY_ANALYSIS, requestJson);
        job.sourceStructuredPlan = sourcePlan;
        job.sourceLegalReview = sourceLegalReview;
        job.idempotencyKey = idempotencyKey;
        job.requestFingerprint = requestFingerprint;
        return job;
    }

    public static AnalysisJob queuedPersonaRecommendation(
        Project project, StructuredPlan sourcePlan, FeasibilityAssessment sourceFeasibilityAssessment,
        String requestJson, String idempotencyKey, String requestFingerprint
    ) {
        AnalysisJob job = new AnalysisJob(project, JobType.PERSONA_RECOMMENDATION, requestJson);
        job.sourceStructuredPlan = sourcePlan;
        job.sourceFeasibilityAssessment = sourceFeasibilityAssessment;
        job.idempotencyKey = idempotencyKey;
        job.requestFingerprint = requestFingerprint;
        return job;
    }

    public static AnalysisJob queuedSystemSmoke(
        Project project,
        String requestJson,
        String idempotencyKey,
        String requestFingerprint,
        AnalysisJob rerunOfJob
    ) {
        AnalysisJob job = new AnalysisJob(
            project,
            JobType.SYSTEM_SMOKE_TEST,
            requestJson
        );
        job.idempotencyKey = idempotencyKey;
        job.requestFingerprint = requestFingerprint;
        job.rerunOfJob = rerunOfJob;
        return job;
    }

    public static AnalysisJob queuedSystemArtifactSmoke(
        Project project,
        String requestJson,
        String idempotencyKey,
        String requestFingerprint
    ) {
        AnalysisJob job = new AnalysisJob(
            project,
            JobType.SYSTEM_ARTIFACT_SMOKE_TEST,
            requestJson
        );
        job.idempotencyKey = idempotencyKey;
        job.requestFingerprint = requestFingerprint;
        return job;
    }

    public static AnalysisJob queuedMarketingGeneration(
        Project project,
        String requestJson,
        String idempotencyKey,
        String requestFingerprint,
        AnalysisJob rerunOfJob
    ) {
        AnalysisJob job = new AnalysisJob(
            project,
            JobType.MARKETING_GENERATION,
            requestJson
        );
        job.idempotencyKey = idempotencyKey;
        job.requestFingerprint = requestFingerprint;
        job.rerunOfJob = rerunOfJob;
        return job;
    }

    public boolean hasSameIdempotentRequest(String fingerprint) {
        return Objects.equals(this.requestFingerprint, fingerprint);
    }

    public boolean isTerminalStatus() {
        return isTerminal();
    }

    public void updateProgress(int progress) {
        if (progress < 0 || progress > 100) throw new IllegalArgumentException("progress must be between 0 and 100");
        this.progress = progress;
    }

    public void start(String currentStep) {
        requireStatus(JobStatus.QUEUED);
        this.status = JobStatus.RUNNING;
        this.currentStep = currentStep;
        this.startedAt = LocalDateTime.now();
    }

    public void succeed(String resultReferenceType, Long resultReferenceId) {
        requireStatus(JobStatus.RUNNING);
        this.status = JobStatus.SUCCEEDED;
        this.progress = 100;
        this.resultReferenceType = resultReferenceType;
        this.resultReferenceId = resultReferenceId;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorCode, String safeMessage) {
        if (isTerminal()) {
            throw new IllegalStateException("terminal job cannot fail again");
        }
        this.status = JobStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = safeMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (isTerminal()) {
            throw new IllegalStateException("terminal job cannot be canceled");
        }
        this.status = JobStatus.CANCELED;
        this.completedAt = LocalDateTime.now();
    }

    public void claim(String workerId, String token, LocalDateTime now) {
        requireStatus(JobStatus.QUEUED);
        if (jobType == JobType.DOCUMENT_PARSE && sourceDocumentVersion == null) {
            throw new IllegalStateException("document parse job requires a source document version");
        }
        if (jobType == JobType.LEGAL_REVIEW && sourceStructuredPlan == null) {
            throw new IllegalStateException("legal review job requires a structured plan");
        }
        if (jobType == JobType.FEASIBILITY_ANALYSIS
            && (sourceStructuredPlan == null || sourceLegalReview == null)) {
            throw new IllegalStateException(
                "feasibility assessment job requires a structured plan and legal review");
        }
        if (jobType == JobType.PERSONA_RECOMMENDATION
            && (sourceStructuredPlan == null || sourceFeasibilityAssessment == null)) {
            throw new IllegalStateException(
                "persona recommendation job requires a structured plan and feasibility assessment");
        }
        if (nextAttemptAt != null && nextAttemptAt.isAfter(now)) {
            throw new IllegalStateException("job is not due");
        }
        this.status = JobStatus.RUNNING;
        this.progress = Math.max(progress, 5);
        this.currentStep = "CLAIMED";
        this.attemptCount += 1;
        this.retryCount = Math.max(0, attemptCount - 1);
        this.startedAt = now;
        this.completedAt = null;
        this.claimedAt = now;
        this.claimedBy = workerId;
        this.heartbeatAt = now;
        this.claimToken = token;
        this.nextAttemptAt = null;
        this.errorCode = null;
        this.errorMessage = null;
        this.lastErrorCode = null;
        this.retryable = null;
    }

    public void advance(
        String token,
        int attempt,
        int nextProgress,
        String step,
        LocalDateTime now
    ) {
        requireCurrentClaim(token, attempt);
        if (nextProgress < progress || nextProgress > 100) {
            throw new IllegalArgumentException("job progress must be monotonic and at most 100");
        }
        this.progress = nextProgress;
        this.currentStep = step;
        this.heartbeatAt = now;
    }

    public void setExternalRequestId(String token, int attempt, String requestId) {
        requireCurrentClaim(token, attempt);
        this.externalRequestId = requestId;
    }

    public void complete(
        String token,
        int attempt,
        JobStatus completionStatus,
        String referenceType,
        Long referenceId,
        LocalDateTime now
    ) {
        requireCurrentClaim(token, attempt);
        if (completionStatus != JobStatus.SUCCEEDED && completionStatus != JobStatus.PARTIAL) {
            throw new IllegalArgumentException("completion status must be SUCCEEDED or PARTIAL");
        }
        this.status = completionStatus;
        this.progress = 100;
        this.currentStep = "COMPLETED";
        this.resultReferenceType = referenceType;
        this.resultReferenceId = referenceId;
        this.completedAt = now;
        this.heartbeatAt = now;
        this.retryable = false;
        clearClaim();
    }

    public void scheduleRetry(
        String token,
        int attempt,
        String safeErrorCode,
        String safeMessage,
        LocalDateTime nextAttemptAt,
        LocalDateTime now
    ) {
        requireCurrentClaim(token, attempt);
        this.status = JobStatus.QUEUED;
        this.currentStep = "RETRY_SCHEDULED";
        this.errorCode = safeErrorCode;
        this.errorMessage = safeMessage;
        this.lastErrorCode = safeErrorCode;
        this.retryable = true;
        this.nextAttemptAt = nextAttemptAt;
        this.heartbeatAt = now;
        clearClaim();
    }

    public void failAttempt(
        String token,
        int attempt,
        String safeErrorCode,
        String safeMessage,
        boolean wasRetryable,
        LocalDateTime now
    ) {
        requireCurrentClaim(token, attempt);
        this.status = JobStatus.FAILED;
        this.currentStep = "FAILED";
        this.errorCode = safeErrorCode;
        this.errorMessage = safeMessage;
        this.lastErrorCode = safeErrorCode;
        this.retryable = wasRetryable;
        this.completedAt = now;
        this.heartbeatAt = now;
        clearClaim();
    }

    public void recoverStaleForRetry(LocalDateTime now) {
        requireStatus(JobStatus.RUNNING);
        this.status = JobStatus.QUEUED;
        this.currentStep = "STALE_RECOVERED";
        this.lastErrorCode = "STALE_EXECUTION";
        this.errorCode = "STALE_EXECUTION";
        this.errorMessage = "중단된 분석 작업을 다시 예약했습니다.";
        this.retryable = true;
        this.nextAttemptAt = now;
        clearClaim();
    }

    public void failStale(LocalDateTime now) {
        requireStatus(JobStatus.RUNNING);
        this.status = JobStatus.FAILED;
        this.currentStep = "FAILED";
        this.lastErrorCode = "STALE_EXECUTION";
        this.errorCode = "STALE_EXECUTION";
        this.errorMessage = "분석 작업의 최대 시도 횟수를 초과했습니다.";
        this.retryable = false;
        this.completedAt = now;
        clearClaim();
    }

    public boolean hasCurrentClaim(String token, int attempt) {
        return status == JobStatus.RUNNING
            && Objects.equals(claimToken, token)
            && attemptCount == attempt;
    }

    public boolean isStaleBefore(LocalDateTime threshold) {
        LocalDateTime leaseTime = heartbeatAt != null ? heartbeatAt : claimedAt;
        return status == JobStatus.RUNNING
            && leaseTime != null
            && leaseTime.isBefore(threshold);
    }

    private void requireCurrentClaim(String token, int attempt) {
        if (!hasCurrentClaim(token, attempt)) {
            throw new IllegalStateException("job claim is no longer current");
        }
    }

    private void clearClaim() {
        this.claimToken = null;
        this.claimedBy = null;
        this.claimedAt = null;
    }

    private void requireStatus(JobStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException("job status must be " + expected);
        }
    }

    private boolean isTerminal() {
        return status == JobStatus.SUCCEEDED
            || status == JobStatus.FAILED
            || status == JobStatus.CANCELED;
    }
}
