package com.aivle.backend.pipeline.idea;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.idea.application.IdeaBriefReadinessCalculator;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefFieldCatalog;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IdeaBriefReadinessTests {
    @Test
    void countsTheWholeRequiredCatalogInsteadOfOnlyPersistedRows() {
        IdeaBrief brief = IdeaBrief.initial(null, 7L);
        brief.applyAssessment("summary", "[]", "[]", "READY_FOR_REVIEW", 100);
        IdeaBriefField problem = IdeaBriefField.userValue(
            brief, "problem", "food waste", IdeaDecisionState.PREFERRED);

        var result = new IdeaBriefReadinessCalculator(new ObjectMapper())
            .calculate(brief, List.of(problem), List.of());

        assertThat(result.totalRequiredFieldCount()).isEqualTo(10);
        assertThat(result.completedRequiredFieldCount()).isEqualTo(1);
        assertThat(result.missingFieldKeys()).contains("targetCustomers", "payment", "requiredPartners");
        assertThat(result.readyForConfirm()).isFalse();
        assertThat(problem.getProvenance()).isEqualTo(IdeaFieldProvenance.USER_CONFIRMED);
    }

    @Test
    void providerNeedsInputIsAdvisoryWhenBackendRequirementsAreSatisfied() {
        IdeaBrief brief = IdeaBrief.initial(null, 7L);
        brief.applyAssessment("summary", "[]", "[]", "NEEDS_INPUT", 20);

        var result = new IdeaBriefReadinessCalculator(new ObjectMapper())
            .calculate(brief, completeRequiredFields(brief, false), List.of(), true);

        assertThat(result.missingFieldKeys()).isEmpty();
        assertThat(result.unansweredQuestionCount()).isZero();
        assertThat(result.contradictionCount()).isZero();
        assertThat(result.readyForConfirm()).isTrue();
    }

    @Test
    void blockingContradictionPreventsConfirmButNotReviewEligibility() {
        IdeaBrief brief = IdeaBrief.initial(null, 7L);
        brief.applyAssessment("summary", """
            [{"fieldKeys":["problem","targetCustomers"],"summary":"conflict"}]
            """, "[]", "READY_FOR_REVIEW", 80);

        var result = new IdeaBriefReadinessCalculator(new ObjectMapper())
            .calculate(brief, completeRequiredFields(brief, true), List.of(), true);

        assertThat(result.missingFieldKeys()).isEmpty();
        assertThat(result.contradictionCount()).isEqualTo(1);
        assertThat(result.readyForConfirm()).isFalse();
    }

    private List<IdeaBriefField> completeRequiredFields(IdeaBrief brief, boolean unresolvedContradiction) {
        return IdeaBriefFieldCatalog.fields().stream()
            .filter(IdeaBriefFieldCatalog.FieldDefinition::requiredForConcept)
            .map(definition -> unresolvedContradiction
                && (definition.key().equals("problem") || definition.key().equals("targetCustomers"))
                    ? IdeaBriefField.aiProposal(brief, definition.key(), "value",
                        IdeaDecisionState.PREFERRED, IdeaFieldProvenance.AI_PROPOSED)
                    : IdeaBriefField.userValue(brief, definition.key(), "value", IdeaDecisionState.PREFERRED))
            .toList();
    }
}
