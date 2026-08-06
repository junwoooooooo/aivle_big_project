package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "concepts") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Concept extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_version_id", nullable = false) private IdeaVersion ideaVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "generation_run_id", nullable = false) private ConceptGenerationRun generationRun;
    @Column(nullable = false) private int displayOrder;
    public static Concept create(Project project, IdeaVersion ideaVersion, ConceptGenerationRun run, int order) {
        Concept value = new Concept(); value.project = project; value.ideaVersion = ideaVersion; value.generationRun = run; value.displayOrder = order; return value;
    }
}
