package com.aivle.backend.analysis.financial.entity;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityAssessment;
import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.document.entity.DocumentVersion;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable source references plus user-confirmed assumptions for one financial scenario set. */
@Entity
@Table(name = "financial_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialAnalysis extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feasibility_assessment_id")
    private FeasibilityAssessment feasibilityAssessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structured_plan_id")
    private StructuredPlan structuredPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_version_id")
    private DocumentVersion sourceDocumentVersion;

    @Column(nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinancialStatus status;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false)
    private Integer analysisPeriodMonths;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String assumptionsJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String scenariosJson;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceSnapshotJson;

    @Column(length = 64) private String inputHash;
    @Column(length = 64) private String resultHash;
    private LocalDateTime completedAt;

    public static FinancialAnalysis draft(
        Project project, User createdBy, FeasibilityAssessment feasibility,
        StructuredPlan plan, DocumentVersion sourceVersion, int versionNumber,
        String title, String currency, int periodMonths, String assumptionsJson,
        String scenariosJson, String sourceSnapshotJson, String inputHash
    ) {
        FinancialAnalysis value = new FinancialAnalysis();
        value.project = project;
        value.createdBy = createdBy;
        value.feasibilityAssessment = feasibility;
        value.structuredPlan = plan;
        value.sourceDocumentVersion = sourceVersion;
        value.versionNumber = versionNumber;
        value.status = FinancialStatus.DRAFT;
        value.title = title;
        value.currency = currency;
        value.analysisPeriodMonths = periodMonths;
        value.assumptionsJson = assumptionsJson;
        value.scenariosJson = scenariosJson;
        value.sourceSnapshotJson = sourceSnapshotJson;
        value.inputHash = inputHash;
        return value;
    }

    public void updateDraft(String title, int periodMonths, String assumptionsJson,
                            String scenariosJson, String inputHash) {
        if (status == FinancialStatus.COMPLETED) {
            throw new IllegalStateException("completed financial analysis is immutable");
        }
        this.title = title;
        this.analysisPeriodMonths = periodMonths;
        this.assumptionsJson = assumptionsJson;
        this.scenariosJson = scenariosJson;
        this.inputHash = inputHash;
        this.status = FinancialStatus.DRAFT;
        this.resultJson = null;
        this.summaryJson = null;
        this.resultHash = null;
        this.completedAt = null;
    }

    public void complete(String resultJson, String summaryJson, String resultHash, LocalDateTime now) {
        this.status = FinancialStatus.COMPLETED;
        this.resultJson = resultJson;
        this.summaryJson = summaryJson;
        this.resultHash = resultHash;
        this.completedAt = now;
    }
}
