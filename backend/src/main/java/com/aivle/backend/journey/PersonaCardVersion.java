package com.aivle.backend.journey;
import com.aivle.backend.common.entity.BaseEntity; import com.aivle.backend.project.entity.Project; import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="persona_card_versions") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PersonaCardVersion extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false) private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="concept_version_id",nullable=false) private ConceptVersion conceptVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="persona_card_id",nullable=false) private PersonaCard personaCard;
    @Column(nullable=false) private int versionNumber; @Column(nullable=false,length=200) private String name; @Column(nullable=false,length=200) private String shortLabel;
    @Column(name="role_and_context_json",nullable=false,columnDefinition="TEXT") private String roleAndContextJson;
    @Column(name="problem_and_needs_json",nullable=false,columnDefinition="TEXT") private String problemAndNeedsJson;
    @Column(name="behavior_and_decision_json",nullable=false,columnDefinition="TEXT") private String behaviorAndDecisionJson;
    @Column(name="interview_focus_json",nullable=false,columnDefinition="TEXT") private String interviewFocusJson;
    public static PersonaCardVersion create(Project p,ConceptVersion concept,PersonaCard card,String name,String label,String role,String problems,String behavior,String focus){PersonaCardVersion v=new PersonaCardVersion();v.project=p;v.conceptVersion=concept;v.personaCard=card;v.versionNumber=1;v.name=name;v.shortLabel=label;v.roleAndContextJson=role;v.problemAndNeedsJson=problems;v.behaviorAndDecisionJson=behavior;v.interviewFocusJson=focus;return v;}
}
