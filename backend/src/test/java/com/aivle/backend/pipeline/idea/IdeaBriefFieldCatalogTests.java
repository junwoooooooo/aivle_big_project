package com.aivle.backend.pipeline.idea;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.idea.domain.IdeaBriefFieldCatalog;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdeaBriefFieldCatalogTests {
    @Test
    void v2SeedHasExactlyThreeRequiredFieldsAndStructuredOptionalLocks() {
        assertThat(IdeaBriefFieldCatalog.fields()).extracting(IdeaBriefFieldCatalog.FieldDefinition::key)
            .containsExactlyElementsOf(List.of(
                "ideaOverview", "problem", "targetUsers", "targetRegion", "knownCompetitors",
                "revenueModel", "price", "channels", "differentiators", "budgetConstraint",
                "teamConstraint", "timelineConstraint", "otherConstraint"
            ));
        assertThat(IdeaBriefFieldCatalog.fields())
            .filteredOn(IdeaBriefFieldCatalog.FieldDefinition::requiredForConcept)
            .extracting(IdeaBriefFieldCatalog.FieldDefinition::key)
            .containsExactly("ideaOverview", "problem", "targetUsers");
        assertThat(IdeaBriefFieldCatalog.require("targetRegion").defaultDecisionState())
            .isEqualTo(IdeaDecisionState.OPEN);
        assertThat(IdeaBriefFieldCatalog.require("targetRegion").allowedQuestionTypes()).isEmpty();
        assertThat(IdeaBriefFieldCatalog.contains("payment")).isFalse();
        assertThat(IdeaBriefFieldCatalog.contains("personalData")).isFalse();
    }
}
