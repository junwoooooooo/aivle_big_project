package com.aivle.backend.pipeline.idea;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.idea.application.IdeaBriefReadinessCalculator;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
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
}
