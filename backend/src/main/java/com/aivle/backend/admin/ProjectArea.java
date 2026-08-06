package com.aivle.backend.admin;

import com.aivle.backend.common.entity.ProjectStage;
import java.util.EnumSet;
import java.util.Set;

public enum ProjectArea {
    PLAN(EnumSet.of(ProjectStage.DOCUMENT, ProjectStage.STRUCTURING)),
    REVIEW(EnumSet.of(ProjectStage.LEGAL_REVIEW, ProjectStage.FEASIBILITY, ProjectStage.FINANCIAL)),
    VALIDATE(EnumSet.of(ProjectStage.PERSONA_CONFIGURATION, ProjectStage.PANEL_SURVEY, ProjectStage.PANEL_DISCUSSION)),
    REPORT(EnumSet.of(ProjectStage.REPORT, ProjectStage.MARKETING, ProjectStage.COMPLETED));

    private final Set<ProjectStage> stages;

    ProjectArea(Set<ProjectStage> stages) {
        this.stages = Set.copyOf(stages);
    }

    public Set<ProjectStage> stages() {
        return stages;
    }

    public static ProjectArea from(ProjectStage stage) {
        for (ProjectArea area : values()) {
            if (area.stages.contains(stage)) return area;
        }
        throw new IllegalArgumentException("Unsupported project stage: " + stage);
    }
}
