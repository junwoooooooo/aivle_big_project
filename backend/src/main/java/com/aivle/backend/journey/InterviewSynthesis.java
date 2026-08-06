package com.aivle.backend.journey;
import com.aivle.backend.common.entity.BaseEntity; import com.aivle.backend.project.entity.Project; import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="interview_syntheses") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class InterviewSynthesis extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false) private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="concept_version_id",nullable=false) private ConceptVersion conceptVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="study_id",nullable=false) private PersonaStudy study;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="run_id",nullable=false) private InterviewSynthesisRun run;
    @Column(name="common_themes_json",nullable=false,columnDefinition="TEXT") private String commonThemesJson;
    @Column(name="conflicting_views_json",nullable=false,columnDefinition="TEXT") private String conflictingViewsJson;
    @Column(name="critical_needs_json",nullable=false,columnDefinition="TEXT") private String criticalNeedsJson;
    @Column(name="decision_barriers_json",nullable=false,columnDefinition="TEXT") private String decisionBarriersJson;
    @Column(name="implications_json",nullable=false,columnDefinition="TEXT") private String implicationsJson;
    @Column(name="research_needs_json",nullable=false,columnDefinition="TEXT") private String researchNeedsJson;
    public static InterviewSynthesis create(Project p,ConceptVersion concept,PersonaStudy study,InterviewSynthesisRun run,String common,String conflicts,String needs,String barriers,String implications,String research){InterviewSynthesis v=new InterviewSynthesis();v.project=p;v.conceptVersion=concept;v.study=study;v.run=run;v.commonThemesJson=common;v.conflictingViewsJson=conflicts;v.criticalNeedsJson=needs;v.decisionBarriersJson=barriers;v.implicationsJson=implications;v.researchNeedsJson=research;return v;}
}
