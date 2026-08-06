package com.aivle.backend.journey;

import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "concept_generation_runs") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptGenerationRun extends ConceptAiRunBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    public static ConceptGenerationRun pending(Project project, IdeaVersion ideaVersion) {
        ConceptGenerationRun run = new ConceptGenerationRun(); run.initialize(project, ideaVersion); return run;
    }
}
