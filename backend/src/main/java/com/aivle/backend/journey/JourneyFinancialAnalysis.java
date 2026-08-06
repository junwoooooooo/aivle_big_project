package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "financial_analyses") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JourneyFinancialAnalysis extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "journey_idea_version_id") private IdeaVersion ideaVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "journey_concept_version_id") private ConceptVersion conceptVersion;
    @Column(nullable = false) private Integer versionNumber;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 10) private String currency;
    @Column(nullable = false) private Integer analysisPeriodMonths;
    @Column(nullable = false, columnDefinition = "TEXT") private String assumptionsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String scenariosJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String sourceSnapshotJson;
    @Column(columnDefinition = "TEXT") private String resultJson;
    private LocalDateTime completedAt;
    @Column(precision=19, scale=2) private BigDecimal unitPrice;
    private Integer monthlyCustomers;
    @Column(precision=19, scale=2) private BigDecimal variableCostPerCustomer;
    @Column(precision=19, scale=2) private BigDecimal monthlyFixedCost;
    @Column(precision=19, scale=2) private BigDecimal initialInvestment;
    @Column(precision=19, scale=2) private BigDecimal monthlyRevenue;
    @Column(precision=19, scale=2) private BigDecimal monthlyVariableCost;
    @Column(precision=19, scale=2) private BigDecimal monthlyTotalCost;
    @Column(precision=19, scale=2) private BigDecimal monthlyOperatingProfit;
    private Integer breakEvenCustomers;
    @Column(precision=10, scale=2) private BigDecimal paybackMonths;
    public static JourneyFinancialAnalysis create(Project p, IdeaVersion idea, ConceptVersion concept, String title,
            BigDecimal price, int customers, BigDecimal variableCost, BigDecimal fixedCost, BigDecimal investment,
            BigDecimal revenue, BigDecimal totalVariable, BigDecimal totalCost, BigDecimal profit,
            int breakEvenCustomers, BigDecimal payback, String inputs, String result) {
        JourneyFinancialAnalysis v=new JourneyFinancialAnalysis(); v.project=p; v.ideaVersion=idea; v.conceptVersion=concept;
        v.versionNumber=1; v.status="COMPLETED"; v.title=title; v.currency="KRW"; v.analysisPeriodMonths=12;
        v.assumptionsJson=inputs; v.scenariosJson="[]"; v.sourceSnapshotJson="{}"; v.resultJson=result; v.completedAt=LocalDateTime.now();
        v.unitPrice=price; v.monthlyCustomers=customers; v.variableCostPerCustomer=variableCost; v.monthlyFixedCost=fixedCost;
        v.initialInvestment=investment; v.monthlyRevenue=revenue; v.monthlyVariableCost=totalVariable;
        v.monthlyTotalCost=totalCost; v.monthlyOperatingProfit=profit; v.breakEvenCustomers=breakEvenCustomers; v.paybackMonths=payback; return v;
    }
}
