package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.conceptportfolio.application.*;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioResultContract.ContractViolation;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import com.aivle.backend.pipeline.idea.domain.*;
import java.util.List;
import java.time.Duration;
import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.pipeline.conceptportfolio.worker.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ConceptPortfolioContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void confirmedIdeaBriefBuildsExactP1InputWithoutSlotFields() {
        IdeaBrief brief = mock(IdeaBrief.class);
        when(brief.isConfirmed()).thenReturn(true);
        when(brief.getId()).thenReturn("brief-confirmed");
        when(brief.getInterpretationJson()).thenReturn("{\"summary\":\"확정 해석\"}");
        List<IdeaBriefField> fields = List.of(
            field("ideaOverview", "예약 확인 자동화", IdeaFieldProvenance.USER_CONFIRMED, IdeaDecisionState.LOCKED),
            field("problem", "반복 확인 업무", IdeaFieldProvenance.USER_INPUT, IdeaDecisionState.LOCKED),
            field("targetUsers", "소형 매장", IdeaFieldProvenance.USER_CONFIRMED, IdeaDecisionState.LOCKED),
            field("channels", "웹", IdeaFieldProvenance.AI_PROPOSED, IdeaDecisionState.REVIEWABLE)
        );

        JsonNode input = new ConceptPortfolioSeedBuilder(mapper).build(brief, fields, 4).value();

        assertThat(input.path("maxConcepts").intValue()).isEqualTo(4);
        assertThat(input.path("seed").path("ideaOverview").asText()).isEqualTo("예약 확인 자동화");
        assertThat(input.path("seed").path("problem").asText()).isEqualTo("반복 확인 업무");
        assertThat(input.path("seed").path("targetUsers").asText()).isEqualTo("소형 매장");
        assertThat(input.path("seed").path("interpretation").path("summary").asText()).isEqualTo("확정 해석");
        JsonNode optional = input.path("seed").path("fields").get(3);
        assertThat(optional.path("source").asText()).isEqualTo("AI_PROPOSED");
        assertThat(optional.path("decisionState").asText()).isEqualTo("REVIEWABLE");
        assertThat(input.toString()).doesNotContain("slot", "variationFocus");
    }

    @Test
    void mapsReadyLimitedOpenInputAndTechnicalFailuresDeterministically() {
        ConceptPortfolioProductStatusMapper mapper = new ConceptPortfolioProductStatusMapper();
        assertThat(mapper.map(result("READY_LIMITED", 2, 0, null)))
            .isEqualTo(ConceptPortfolioRunStatus.RESULTS_AVAILABLE);
        assertThat(mapper.map(result("READY_LIMITED", 2, 1, null)))
            .isEqualTo(ConceptPortfolioRunStatus.RESULTS_WITH_OPEN_INPUT);
        assertThat(mapper.map(result("FAILED", 0, 1, null)))
            .isEqualTo(ConceptPortfolioRunStatus.NEEDS_INPUT);
        assertThat(mapper.map(result("FAILED", 2, 1, null)))
            .isEqualTo(ConceptPortfolioRunStatus.FAILED);
        assertThat(mapper.map(result("NEEDS_INPUT", 0, 0, null)))
            .isEqualTo(ConceptPortfolioRunStatus.NEEDS_INPUT);
        assertThat(mapper.map(result("FAILED", 0, 0, "RESULT_SCHEMA_INVALID")))
            .isEqualTo(ConceptPortfolioRunStatus.FAILED);
        assertThat(mapper.map(result("FAILED", 0, 0, "NO_LEGAL_READY_CANDIDATES")))
            .isEqualTo(ConceptPortfolioRunStatus.FAILED);
    }

    @Test
    void enforcesSafeTimeoutOrderingAndFourteenMinuteDefaultDeadline() {
        ConceptPortfolioExecutionProperties defaults = new ConceptPortfolioExecutionProperties(
            null, null, null, null, null, null);
        AiServerProperties ai = new AiServerProperties("http://localhost", Duration.ofSeconds(3),
            Duration.ofSeconds(30), Duration.ofMinutes(15), "token");

        assertThat(defaults.aiDeadline()).isEqualTo(Duration.ofMinutes(14));
        assertThatCode(() -> new ConceptPortfolioTimingValidator(defaults, ai))
            .doesNotThrowAnyException();
        ConceptPortfolioExecutionProperties invalid = new ConceptPortfolioExecutionProperties(
            Duration.ofSeconds(90), Duration.ofSeconds(20), Duration.ofMinutes(20),
            Duration.ofMinutes(15), 2, 4);
        assertThatThrownBy(() -> new ConceptPortfolioTimingValidator(invalid, ai))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUserSelectionAndArtifactWithoutSharedPlan() {
        ConceptPortfolioResultContract contract = new ConceptPortfolioResultContract();
        ObjectNode selected = baseContract();
        selected.put("userSelectedConceptId", "candidate-1");
        assertThatThrownBy(() -> contract.validate(selected)).isInstanceOf(ContractViolation.class);

        ObjectNode missingPlan = baseContract();
        ObjectNode context = missingPlan.putObject("continuationContext");
        context.put("contextVersion", "1.0");
        context.putArray("plans").addObject().put("planId", "plan-1");
        missingPlan.putArray("continuationArtifacts").addObject()
            .put("candidateId", "candidate-1").put("planId", "other-plan");
        assertThatThrownBy(() -> contract.validate(missingPlan)).isInstanceOf(ContractViolation.class);
    }

    private IdeaBriefField field(String key, String value, IdeaFieldProvenance source,
            IdeaDecisionState state) {
        IdeaBriefField field = mock(IdeaBriefField.class);
        when(field.getFieldKey()).thenReturn(key);
        when(field.getFieldValue()).thenReturn(value);
        when(field.getProvenance()).thenReturn(source);
        when(field.getDecisionState()).thenReturn(state);
        return field;
    }

    private ObjectNode result(String engineStatus, int produced, int candidateInputs, String failureCode) {
        ObjectNode result = mapper.createObjectNode();
        result.put("engineStatus", engineStatus);
        result.put("producedConceptCount", produced);
        var inputs = result.putArray("requiredInputs");
        for (int index = 0; index < candidateInputs; index++) {
            inputs.addObject().put("scope", "CANDIDATE");
        }
        ObjectNode summary = result.putObject("runSummary");
        if (failureCode == null) summary.putNull("failureCode"); else summary.put("failureCode", failureCode);
        return result;
    }

    private ObjectNode baseContract() {
        ObjectNode result = mapper.createObjectNode();
        result.put("contract", "concept-portfolio-v2-production-result-v1");
        result.put("contractVersion", "1.0");
        result.put("schemaVersion", "1.0");
        result.put("requestedMaxConcepts", 5);
        result.put("producedConceptCount", 0);
        result.putArray("concepts");
        result.putNull("userSelectedConceptId");
        result.putArray("requiredInputs");
        result.putArray("legalSummaries");
        result.putArray("continuationArtifacts");
        result.putNull("continuationContext");
        return result;
    }
}
