package com.aivle.backend.journey;
import com.aivle.backend.common.entity.BaseEntity; import com.aivle.backend.project.entity.Project; import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="persona_cards") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PersonaCard extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false) private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="concept_version_id",nullable=false) private ConceptVersion conceptVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="study_id",nullable=false) private PersonaStudy study;
    @Column(nullable=false) private int displayOrder; @Column(nullable=false) private boolean selected;
    public static PersonaCard create(Project p,ConceptVersion concept,PersonaStudy study,int order){PersonaCard v=new PersonaCard();v.project=p;v.conceptVersion=concept;v.study=study;v.displayOrder=order;return v;}
    public void select(boolean value){selected=value;}
}
