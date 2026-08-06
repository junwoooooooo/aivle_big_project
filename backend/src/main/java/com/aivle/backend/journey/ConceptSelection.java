package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "concept_selections") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptSelection extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_version_id", nullable = false) private IdeaVersion ideaVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "concept_version_id", nullable = false) private ConceptVersion conceptVersion;
    @Column(nullable = false, columnDefinition = "TEXT") private String reason;
    public static ConceptSelection create(Project p, IdeaVersion idea, ConceptVersion concept, String reason) {
        ConceptSelection v=new ConceptSelection(); v.project=p; v.ideaVersion=idea; v.conceptVersion=concept; v.reason=reason; return v;
    }
}
