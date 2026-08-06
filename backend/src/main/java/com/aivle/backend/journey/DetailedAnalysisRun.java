package com.aivle.backend.journey;

import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "detailed_analysis_runs") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetailedAnalysisRun extends ConceptAiRunBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    public static DetailedAnalysisRun pending(Project project, IdeaVersion ideaVersion) {
        DetailedAnalysisRun run = new DetailedAnalysisRun(); run.initialize(project, ideaVersion); return run;
    }
}
