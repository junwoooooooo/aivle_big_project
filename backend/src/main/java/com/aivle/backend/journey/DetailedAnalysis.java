package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "detailed_analyses") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetailedAnalysis extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_version_id", nullable = false) private IdeaVersion ideaVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "run_id", nullable = false) private DetailedAnalysisRun run;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "concept_version_id", nullable = false) private ConceptVersion conceptVersion;
    @Column(nullable = false, columnDefinition = "TEXT") private String marketAnalysis;
    @Column(nullable = false, columnDefinition = "TEXT") private String customerAnalysis;
    @Column(nullable = false, columnDefinition = "TEXT") private String businessModelAnalysis;
    @Column(nullable = false, columnDefinition = "TEXT") private String operationAnalysis;
    @Column(nullable = false, columnDefinition = "TEXT") private String riskAnalysis;
    @Column(nullable = false, columnDefinition = "TEXT") private String recommendation;
    @Column(name = "assumptions_json", nullable = false, columnDefinition = "TEXT") private String assumptionsJson;
    @Column(name = "research_needs_json", nullable = false, columnDefinition = "TEXT") private String researchNeedsJson;
    public static DetailedAnalysis create(Project p, IdeaVersion idea, DetailedAnalysisRun run, ConceptVersion concept,
            String market, String customer, String business, String operation, String risk, String recommendation,
            String assumptions, String research) {
        DetailedAnalysis v=new DetailedAnalysis(); v.project=p; v.ideaVersion=idea; v.run=run; v.conceptVersion=concept;
        v.marketAnalysis=market; v.customerAnalysis=customer; v.businessModelAnalysis=business; v.operationAnalysis=operation;
        v.riskAnalysis=risk; v.recommendation=recommendation; v.assumptionsJson=assumptions; v.researchNeedsJson=research; return v;
    }
}
