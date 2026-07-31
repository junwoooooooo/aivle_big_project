package com.aivle.backend.analysis.financial.entity;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityAssessment;
import com.aivle.backend.analysis.financial.entity.FinancialTypes.Verdict;
import com.aivle.backend.common.entity.*;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 재무 분석 1건. job 하나당 한 행이며, 가정을 다시 확정하면 같은 행을 갱신한다.
 *
 * <p>수명주기는 두 단계다: job이 가정 초안을 만들면 {@code assumptionsJson} 안의 state가
 * NEEDS_ASSUMPTIONS이고 지표는 비어 있다. 사용자가 확정하면 결정론 계산 결과가 채워지고
 * state가 CONFIRMED가 된다. (컬럼 {@code status}는 job 진행 상태이지 가정 상태가 아니다.)
 */
@Entity @Table(name = "financial_analyses")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialAnalysis extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_job_id", nullable = false, unique = true) private AnalysisJob analysisJob;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "structured_plan_id") private StructuredPlan structuredPlan;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "feasibility_assessment_id") private FeasibilityAssessment feasibilityAssessment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private JobStatus status;
    @Column(nullable = false, length = 3) private String currency;
    private Integer analysisPeriodMonths;
    @Column(precision = 19, scale = 2) private BigDecimal expectedRevenue;
    @Column(precision = 19, scale = 2) private BigDecimal expectedCost;
    private Integer breakEvenPointMonths;
    @Column(precision = 10, scale = 4) private BigDecimal roi;
    @Column(precision = 19, scale = 2) private BigDecimal npv;
    @Column(precision = 10, scale = 4) private BigDecimal irr;
    @Enumerated(EnumType.STRING) @Column(length = 40) private Verdict verdict;
    @Column(columnDefinition = "TEXT") private String summary;
    @Column(columnDefinition = "TEXT") private String assumptionsJson;
    @Column(columnDefinition = "TEXT") private String resultJson;
    @Column(columnDefinition = "TEXT") private String narrativeJson;
    @Column(length = 60) private String promptVersion;

    /** job이 가정 초안을 만든 직후. 지표는 아직 없다 — 사용자가 확정해야 계산된다. */
    public static FinancialAnalysis draft(
        Project project, AnalysisJob job, StructuredPlan plan, FeasibilityAssessment feasibility,
        String currency, String assumptionsJson, String narrativeJson, String promptVersion
    ) {
        FinancialAnalysis item = new FinancialAnalysis();
        item.project = project;
        item.analysisJob = job;
        item.structuredPlan = plan;
        item.feasibilityAssessment = feasibility;
        item.status = JobStatus.SUCCEEDED;
        item.currency = currency;
        item.assumptionsJson = assumptionsJson;
        item.narrativeJson = narrativeJson;
        item.promptVersion = promptVersion;
        return item;
    }

    /**
     * 가정 확정 후 계산 결과를 싣는다. 재확정하면 같은 행을 다시 덮는다.
     * 계산되지 않은 지표는 null 그대로 둔다 — 0으로 채우지 않는다.
     */
    public void confirm(
        String assumptionsJson, String resultJson, Verdict verdict, Integer analysisPeriodMonths,
        BigDecimal expectedRevenue, BigDecimal expectedCost, Integer breakEvenPointMonths,
        BigDecimal roi, BigDecimal npv, BigDecimal irr, String summary
    ) {
        this.assumptionsJson = assumptionsJson;
        this.resultJson = resultJson;
        this.verdict = verdict;
        this.analysisPeriodMonths = analysisPeriodMonths;
        this.expectedRevenue = expectedRevenue;
        this.expectedCost = expectedCost;
        this.breakEvenPointMonths = breakEvenPointMonths;
        this.roi = roi;
        this.npv = npv;
        this.irr = irr;
        this.summary = summary;
    }
}
