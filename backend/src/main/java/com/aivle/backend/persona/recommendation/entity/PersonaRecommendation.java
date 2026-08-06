package com.aivle.backend.persona.recommendation.entity;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityAssessment;
import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.document.entity.*;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import static com.aivle.backend.persona.recommendation.entity.PersonaRecommendationTypes.*;

@Entity
@Table(name = "persona_recommendations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonaRecommendation extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_job_id", nullable = false, unique = true)
    private AnalysisJob analysisJob;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "structured_plan_id", nullable = false) private StructuredPlan structuredPlan;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feasibility_assessment_id", nullable = false)
    private FeasibilityAssessment feasibilityAssessment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_document_version_id", nullable = false)
    private DocumentVersion sourceDocumentVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private RecommendationStatus status;
    @Column(length = 100) private String primaryPersonaCode;
    @Column(length = 100) private String secondaryPersonaCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PersonaConfidence confidence;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(nullable = false, columnDefinition = "TEXT") private String disclaimer;
    @Column(nullable = false, length = 100) private String provider;
    @Column(nullable = false, length = 100) private String modelName;
    @Column(nullable = false, length = 100) private String promptVersion;
    @Column(nullable = false, length = 100) private String catalogVersion;
    @Column(nullable = false, length = 64) private String promptHash;
    @Column(nullable = false, length = 64) private String inputHash;
    @Column(nullable = false, length = 64) private String resultHash;
    @Column(nullable = false, columnDefinition = "TEXT") private String inputSnapshotJson;
    @Column(nullable = false) private LocalDateTime completedAt;

    public static PersonaRecommendation completed(
        Project project, AnalysisJob job, StructuredPlan plan,
        FeasibilityAssessment feasibility, RecommendationStatus status,
        String primaryPersonaCode, String secondaryPersonaCode,
        PersonaConfidence confidence, String summary, String disclaimer,
        String provider, String modelName, String promptVersion, String catalogVersion,
        String promptHash, String inputHash, String resultHash, String snapshotJson,
        LocalDateTime completedAt
    ) {
        PersonaRecommendation value = new PersonaRecommendation();
        value.project = project;
        value.analysisJob = job;
        value.structuredPlan = plan;
        value.feasibilityAssessment = feasibility;
        value.sourceDocumentVersion = plan.getSourceDocumentVersion();
        value.status = status;
        value.primaryPersonaCode = primaryPersonaCode;
        value.secondaryPersonaCode = secondaryPersonaCode;
        value.confidence = confidence;
        value.summary = summary;
        value.disclaimer = disclaimer;
        value.provider = provider;
        value.modelName = modelName;
        value.promptVersion = promptVersion;
        value.catalogVersion = catalogVersion;
        value.promptHash = promptHash;
        value.inputHash = inputHash;
        value.resultHash = resultHash;
        value.inputSnapshotJson = snapshotJson;
        value.completedAt = completedAt;
        return value;
    }
}
