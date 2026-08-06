package com.aivle.backend.journey;
import com.aivle.backend.common.entity.BaseEntity; import com.aivle.backend.project.entity.Project; import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="persona_interview_messages") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PersonaInterviewMessage extends BaseEntity {
    public enum Category { ROLE_AND_CONTEXT, PROBLEM_AND_NEEDS, BEHAVIOR_AND_DECISION }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false) private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="concept_version_id",nullable=false) private ConceptVersion conceptVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="interview_id",nullable=false) private PersonaInterview interview;
    @Column(nullable=false) private int sequenceNumber; @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private Category category;
    @Column(nullable=false,columnDefinition="TEXT") private String question; @Column(nullable=false,columnDefinition="TEXT") private String answer;
    public static PersonaInterviewMessage create(Project p,ConceptVersion concept,PersonaInterview interview,int sequence,Category category,String question,String answer){PersonaInterviewMessage v=new PersonaInterviewMessage();v.project=p;v.conceptVersion=concept;v.interview=interview;v.sequenceNumber=sequence;v.category=category;v.question=question;v.answer=answer;return v;}
}
