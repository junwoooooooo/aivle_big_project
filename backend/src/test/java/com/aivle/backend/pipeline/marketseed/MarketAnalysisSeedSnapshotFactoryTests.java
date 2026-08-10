package com.aivle.backend.pipeline.marketseed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalAssessment;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalStatus;
import com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedSnapshotFactory;
import com.aivle.backend.pipeline.selection.domain.ConceptHypothesisDecision;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.selection.domain.HypothesisDecisionStatus;
import com.aivle.backend.pipeline.selection.domain.HypothesisLegalImpact;
import com.aivle.backend.pipeline.selection.domain.HypothesisLegalReviewStatus;
import com.aivle.backend.pipeline.selection.domain.HypothesisType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketAnalysisSeedSnapshotFactoryTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void composesCanonicalSeedConceptDecisionsAndLegalEvidenceSections() {
        ConceptSelection selection = mock(ConceptSelection.class);
        when(selection.getId()).thenReturn(9L); when(selection.getProjectId()).thenReturn(7L);
        Concept concept = mock(Concept.class);
        when(concept.getId()).thenReturn("concept-1");
        when(concept.getSourceSnapshotHash()).thenReturn("sha256:" + "a".repeat(64));
        when(concept.getCanonicalHash()).thenReturn("sha256:" + "b".repeat(64));
        when(concept.getCandidateJson()).thenReturn("""
            {"conceptName":"지역 연결","conceptDefinition":"정의","introduction":"소개","coreValue":"가치",
             "targetUsers":"지역 상점","industryCategory":"지역 서비스","researchScope":"국내 시장",
             "targetRegion":"대한민국","problemScenario":"문제","solutionMechanism":"연결","featureSet":["매칭"],
             "actorRoles":["상점","고객"],"platformRole":"중개","operatingModel":"직접 운영","partnerModel":"지역 제휴",
             "valueSemantics":[{"fieldKey":"targetRegion","source":"CONCEPT_GENERATED","decision":"ACCEPTED"}]}
            """);
        IdeaBrief brief = mock(IdeaBrief.class);
        when(brief.getOverviewText()).thenReturn("지역 상점을 고객과 연결한다");
        when(brief.getInterpretationJson()).thenReturn("{\"industryCategory\":\"지역 서비스\",\"researchScope\":\"국내 시장\",\"usageContext\":\"일상\"}");
        ConceptLegalAssessment legal = mock(ConceptLegalAssessment.class);
        when(legal.getStatus()).thenReturn(ConceptLegalStatus.IMPLEMENTABLE_WITH_CONTROLS);
        when(legal.getSafeSummary()).thenReturn("통제조건을 지키면 구현 가능");
        when(legal.getAssessmentJson()).thenReturn("{\"requiredControls\":[\"고지\"],\"requiredPartnersAndQualifications\":[],\"prohibitedVariants\":[\"무자격 제공\"],\"requiredDisclosures\":[\"중개자 고지\"]}");

        var body = new MarketAnalysisSeedSnapshotFactory(mapper).create("seed-1", Instant.EPOCH,
            selection, concept, brief, List.of(seedField("problem", "폐기 문제"), seedField("targetUsers", "지역 상점")),
            Arrays.stream(HypothesisType.values()).map(this::decision).toList(), legal, List.of());

        assertThat(body.path("contract").asText()).isEqualTo("market-analysis-seed-snapshot-v1");
        assertThat(body.path("schemaVersion").asText()).isEqualTo("2.0");
        assertThat(body.path("createdAt").asText()).isEqualTo("1970-01-01T00:00:00Z");
        assertThat(body.path("originalSeed").path("fields").path("problem").path("source").asText()).isEqualTo("USER_INPUT");
        assertThat(body.path("selectedConcept").path("solution").path("solutionMechanism").asText()).isEqualTo("연결");
        assertThat(body.path("finalHypotheses").path("revenueModel").path("decisionStatus").asText()).isEqualTo("ACCEPTED");
        assertThat(body.path("finalHypotheses").path("targetRegion").path("value").asText()).isEqualTo("확정값");
        assertThat(body.path("legalResult").path("requiredControls").get(0).asText()).isEqualTo("고지");
        assertThat(body.path("legalResult").path("officialEvidenceReferences")).hasSize(1);
        assertThat(body.path("legalResult").path("officialEvidenceReferences").get(0).path("lawName").asText()).isEqualTo("전자상거래법");
    }

    private IdeaBriefField seedField(String key, String value) {
        IdeaBriefField field = mock(IdeaBriefField.class);
        when(field.getFieldKey()).thenReturn(key); when(field.getFieldValue()).thenReturn(value);
        when(field.getDecisionState()).thenReturn(IdeaDecisionState.LOCKED);
        when(field.getProvenance()).thenReturn(IdeaFieldProvenance.USER_INPUT);
        return field;
    }

    private ConceptHypothesisDecision decision(HypothesisType type) {
        ConceptHypothesisDecision value = mock(ConceptHypothesisDecision.class);
        when(value.getHypothesisType()).thenReturn(type);
        when(value.getFinalValueJson()).thenReturn(type == HypothesisType.PRE_MARKET_SOM_SHARE
            ? "{\"targetSharePercent\":2.5,\"horizonYears\":3}"
            : type == HypothesisType.PRE_MARKET_SOM ? "{\"amount\":100000000,\"currency\":\"KRW\"}" : "\"확정값\"");
        when(value.getSource()).thenReturn("AI_HYPOTHESIS");
        when(value.getDecisionStatus()).thenReturn(HypothesisDecisionStatus.ACCEPTED);
        when(value.getProposalVersion()).thenReturn(1);
        when(value.getLegalImpact()).thenReturn(type.legalSensitive() ? HypothesisLegalImpact.LEGAL_SENSITIVE : HypothesisLegalImpact.NON_LEGAL);
        when(value.getLegalReviewStatus()).thenReturn(HypothesisLegalReviewStatus.NOT_REQUIRED);
        when(value.getDecidedAt()).thenReturn(Instant.EPOCH);
        if (type == HypothesisType.REVENUE_MODEL) {
            when(value.getLegalReviewResultJson()).thenReturn("""
                {"status":"IMPLEMENTABLE","officialEvidenceReferences":[{"sourceType":"OFFICIAL_LAW",
                 "lawName":"전자상거래법","articleReference":"제1조","officialSourceUri":"https://www.law.go.kr/법령/전자상거래법",
                 "contentHash":"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"}]}
                """);
        }
        return value;
    }
}
