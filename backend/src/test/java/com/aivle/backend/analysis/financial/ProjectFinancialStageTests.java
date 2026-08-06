package com.aivle.backend.analysis.financial;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.common.entity.ProjectStage;
import com.aivle.backend.project.entity.Project;
import org.junit.jupiter.api.Test;

class ProjectFinancialStageTests {
    @Test
    void feasibilityAdvancesToFinancialAndCompletionAdvancesToPersonaConfiguration() {
        Project project = Project.create(null, "stage", null, null);
        project.enterStructuring();
        project.enterLegalReview();
        project.enterFeasibility();
        assertThat(project.getStage()).isEqualTo(ProjectStage.FEASIBILITY);
        project.enterFinancial();
        assertThat(project.getStage()).isEqualTo(ProjectStage.FINANCIAL);
        project.enterPersonaConfiguration();
        assertThat(project.getStage()).isEqualTo(ProjectStage.PERSONA_CONFIGURATION);
    }

    @Test
    void financialTransitionsDoNotRegressAProjectAlreadyPastFinancial() {
        Project project = Project.create(null, "stage", null, null);
        project.enterStructuring();
        project.enterLegalReview();
        project.enterFeasibility();
        project.enterFinancial();
        project.enterPersonaConfiguration();
        project.enterFinancial();
        assertThat(project.getStage()).isEqualTo(ProjectStage.PERSONA_CONFIGURATION);
    }
}
