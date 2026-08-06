package com.aivle.backend.pipeline.idea;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.idea.domain.IdeaBriefFieldCatalog;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdeaBriefFieldCatalogTests {
    @Test
    void catalogHasTheCanonicalKeysAndAuthorityDefaults() {
        assertThat(IdeaBriefFieldCatalog.fields()).extracting(IdeaBriefFieldCatalog.FieldDefinition::key)
            .containsExactlyElementsOf(List.of(
                "problem", "targetCustomers", "beneficiaries", "usageContext", "expectedOutcome",
                "targetRegion", "fixedConditions", "preferredConditions", "openDecisions", "assumptions",
                "prohibitedMethods", "physicalActivity", "personalData", "payment", "requiredPartners"
            ));
        assertThat(IdeaBriefFieldCatalog.require("fixedConditions").defaultDecisionState())
            .isEqualTo(IdeaDecisionState.LOCKED);
        assertThat(IdeaBriefFieldCatalog.require("prohibitedMethods").defaultDecisionState())
            .isEqualTo(IdeaDecisionState.LOCKED);
        assertThat(IdeaBriefFieldCatalog.require("assumptions").defaultDecisionState())
            .isEqualTo(IdeaDecisionState.ASSUMPTION);
        assertThat(IdeaBriefFieldCatalog.require("payment").regulatorySensitive()).isTrue();
        assertThat(IdeaBriefFieldCatalog.require("payment").allowedQuestionTypes())
            .contains(IdeaQuestionType.UNDECIDED);
    }
}
