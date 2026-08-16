package com.aivle.backend.pipeline.module;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.integration.domain.ModuleType;
import com.aivle.backend.taskrun.domain.TaskType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ActiveSurfaceCleanupTests {
    private static final Path BASELINE = Path.of("src/main/resources/db/migration/V1__new_pipeline_baseline.sql");

    @Test
    void exposesOnlyCurrentExternalModuleEnums() {
        assertThat(Arrays.stream(ModuleType.values()).map(Enum::name)).containsExactly(
            "MARKET_ANALYSIS", "BUSINESS_MODEL", "TECH_OPS", "FINANCIAL_ANALYSIS", "PERSONA_RESPONSE");
        assertThat(Arrays.stream(PipelineModuleType.values()).map(Enum::name)).containsExactly(
            "IDEA", "CONCEPT_PORTFOLIO", "CONCEPT_FACTORY", "CONCEPT_SELECTION", "MARKET_ANALYSIS",
            "BUSINESS_MODEL", "TECH_OPS", "FINANCE", "TWIN_SURVEY", "MARKETING");
    }

    @Test
    void baselineContainsNoPlanningProposalOrFinalizedPlanningSchema() throws IOException {
        String sql = Files.readString(BASELINE, StandardCharsets.UTF_8);
        assertThat(sql).contains("'BUSINESS_MODEL'", "'PERSONA_RESPONSE'");
        assertThat(sql).doesNotContain(
            "selected_concept_snapshots", "planning_change_proposals", "planning_change_decisions",
            "planning_snapshots", "finalized_planning_snapshots",
            "BUSINESS_FINANCIAL", "PERSONA_RESPONSE_TEST");
    }

    @Test
    void jobCenterTaskTypesRemainBoundToCurrentPipelineWork() {
        assertThat(Arrays.stream(TaskType.values()).map(Enum::name)).containsExactly(
            "IDEA_ATTACHMENT_PARSE", "IDEA_BRIEF_DERIVATION", "CONCEPT_PORTFOLIO_V2_RUN",
            "CONCEPT_PORTFOLIO_V2_CONTINUE", "CONCEPT_PORTFOLIO_V2_SELECTION_ACTION", "CONCEPT_FACTORY_RUN",
            "CONCEPT_CANDIDATE", "CONCEPT_DISTINCTNESS_JUDGE", "CONCEPT_LEGAL_REVIEW", "CONCEPT_REDESIGN",
            "CONCEPT_HYPOTHESIS_ALTERNATIVE", "CONCEPT_DELTA_LEGAL_REVIEW", "TECH_OPS_PROPOSAL",
            "TECH_OPS_ADVISORY", "FINANCE_ESTIMATE", "FINANCE_ANALYSIS_REPORT",
            "MARKETING_CONTENT_GENERATION", "MARKETING_VISUAL_GENERATION", "MARKET_RESEARCH",
            "TWIN_SURVEY", "TWIN_STIMULUS_DRAFT");
    }
}
