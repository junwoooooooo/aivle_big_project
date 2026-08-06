package com.aivle.backend.journey;

import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "quick_assessment_runs") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuickAssessmentRun extends ConceptAiRunBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    public static QuickAssessmentRun pending(Project project, IdeaVersion ideaVersion) {
        QuickAssessmentRun run = new QuickAssessmentRun(); run.initialize(project, ideaVersion); return run;
    }
}
