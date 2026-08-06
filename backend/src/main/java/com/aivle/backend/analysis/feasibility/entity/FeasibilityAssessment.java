package com.aivle.backend.analysis.feasibility.entity;

import com.aivle.backend.analysis.legal.entity.LegalReview;
import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.document.entity.DocumentVersion;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import static com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;

@Entity
@Table(name = "feasibility_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeasibilityAssessment extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_job_id", nullable = false, unique = true) private AnalysisJob analysisJob;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "structured_plan_id", nullable = false) private StructuredPlan structuredPlan;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_review_id", nullable = false) private LegalReview legalReview;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_document_version_id", nullable = false) private DocumentVersion sourceDocumentVersion;
    @Column(nullable = false) private Integer versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private AssessmentStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Verdict verdict;
    private Integer overallScore;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Confidence confidence;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(nullable = false, columnDefinition = "TEXT") private String keyStrengthsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String keyRisksJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String validationTasksSummaryJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String disclaimer;
    @Column(nullable = false, length = 100) private String provider;
    @Column(nullable = false, length = 100) private String modelName;
    @Column(nullable = false, length = 100) private String promptVersion;
    @Column(nullable = false, length = 100) private String catalogVersion;
    @Column(nullable = false, length = 64) private String promptHash;
    @Column(nullable = false, length = 64) private String inputHash;
    @Column(nullable = false, length = 64) private String resultHash;
    @Column(nullable = false, columnDefinition = "TEXT") private String inputSnapshotJson;
    private LocalDateTime startedAt;
    @Column(nullable = false) private LocalDateTime completedAt;

    public static FeasibilityAssessment completed(
        Project project, AnalysisJob job, StructuredPlan plan, LegalReview legalReview,
        AssessmentStatus status, Verdict verdict, Integer overallScore, Confidence confidence,
        String summary, String strengthsJson, String risksJson, String taskSummaryJson,
        String disclaimer, String provider, String modelName, String promptVersion,
        String catalogVersion, String promptHash, String inputHash, String resultHash,
        String snapshotJson, LocalDateTime completedAt
    ) {
        FeasibilityAssessment value = new FeasibilityAssessment();
        value.project = project;
        value.analysisJob = job;
        value.structuredPlan = plan;
        value.legalReview = legalReview;
        value.sourceDocumentVersion = plan.getSourceDocumentVersion();
        value.versionNumber = plan.getVersionNumber();
        value.status = status;
        value.verdict = verdict;
        value.overallScore = overallScore;
        value.confidence = confidence;
        value.summary = summary;
        value.keyStrengthsJson = strengthsJson;
        value.keyRisksJson = risksJson;
        value.validationTasksSummaryJson = taskSummaryJson;
        value.disclaimer = disclaimer;
        value.provider = provider;
        value.modelName = modelName;
        value.promptVersion = promptVersion;
        value.catalogVersion = catalogVersion;
        value.promptHash = promptHash;
        value.inputHash = inputHash;
        value.resultHash = resultHash;
        value.inputSnapshotJson = snapshotJson;
        value.startedAt = job.getStartedAt();
        value.completedAt = completedAt;
        return value;
    }
}
