package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "quick_assessments") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuickAssessment extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_version_id", nullable = false) private IdeaVersion ideaVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "run_id", nullable = false) private QuickAssessmentRun run;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "concept_version_id", nullable = false) private ConceptVersion conceptVersion;
    @Column(nullable = false) private int marketScore;
    @Column(nullable = false) private int customerValueScore;
    @Column(nullable = false) private int feasibilityScore;
    @Column(nullable = false) private int differentiationScore;
    @Column(nullable = false) private int revenuePotentialScore;
    @Column(nullable = false) private int legalRiskScore;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal overallScore;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(name = "strengths_json", nullable = false, columnDefinition = "TEXT") private String strengthsJson;
    @Column(name = "weaknesses_json", nullable = false, columnDefinition = "TEXT") private String weaknessesJson;
    public static QuickAssessment create(Project p, IdeaVersion idea, QuickAssessmentRun run, ConceptVersion concept,
            int market, int customer, int feasibility, int differentiation, int revenue, int legal, BigDecimal overall,
            String summary, String strengths, String weaknesses) {
        QuickAssessment v = new QuickAssessment(); v.project=p; v.ideaVersion=idea; v.run=run; v.conceptVersion=concept;
        v.marketScore=market; v.customerValueScore=customer; v.feasibilityScore=feasibility; v.differentiationScore=differentiation;
        v.revenuePotentialScore=revenue; v.legalRiskScore=legal; v.overallScore=overall; v.summary=summary;
        v.strengthsJson=strengths; v.weaknessesJson=weaknesses; return v;
    }
}
