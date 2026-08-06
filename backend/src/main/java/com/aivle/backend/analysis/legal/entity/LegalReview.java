package com.aivle.backend.analysis.legal.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.document.entity.DocumentVersion;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name = "legal_reviews")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalReview extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_job_id", nullable = false, unique = true) private AnalysisJob analysisJob;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structured_plan_id", nullable = false) private StructuredPlan structuredPlan;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_document_version_id", nullable = false) private DocumentVersion sourceDocumentVersion;
    @Column(nullable = false) private Integer versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private LegalReviewStatus status;
    @Enumerated(EnumType.STRING) @Column(length = 30) private ReviewDecision decision;
    @Enumerated(EnumType.STRING) @Column(length = 20) private RiskLevel riskLevel;
    @Column(columnDefinition = "TEXT") private String summary;
    @Column(length = 100) private String modelVersion;
    @Column(length = 100) private String knowledgeBaseVersion;
    @Column(columnDefinition = "TEXT") private String rawResultJson;
    @Column(columnDefinition = "TEXT") private String disclaimer;
    @Column(length = 100) private String provider;
    @Column(length = 100) private String modelName;
    @Column(length = 100) private String promptVersion;
    @Column(length = 64) private String promptHash;
    @Column(length = 64) private String rawResultHash;
    @Column(columnDefinition = "TEXT") private String inputSnapshotJson;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public static LegalReview completed(
        Project project, AnalysisJob job, StructuredPlan plan, LegalReviewStatus status,
        RiskLevel riskLevel, String summary, String disclaimer, String provider,
        String modelName, String promptVersion, String promptHash, String rawResultHash,
        String inputSnapshotJson, LocalDateTime now
    ) {
        LegalReview review = new LegalReview();
        review.project = project;
        review.analysisJob = job;
        review.structuredPlan = plan;
        review.sourceDocumentVersion = plan.getSourceDocumentVersion();
        review.versionNumber = plan.getVersionNumber();
        review.status = status;
        review.riskLevel = riskLevel;
        review.summary = summary;
        review.disclaimer = disclaimer;
        review.provider = provider;
        review.modelName = modelName;
        review.promptVersion = promptVersion;
        review.promptHash = promptHash;
        review.rawResultHash = rawResultHash;
        review.inputSnapshotJson = inputSnapshotJson;
        review.startedAt = job.getStartedAt();
        review.completedAt = now;
        return review;
    }
}
