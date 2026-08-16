package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.*;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.market.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

class ConceptRefinementMaterialFactoryTests {
    private static final String HASH = "sha256:" + "a".repeat(64);

    @Test
    void exactSeedFinalValuesAndPinnedBmPlanAreTheEditableAuthority() {
        Fixture f = fixture();
        var input = f.factory.input(41L, f.selection, f.source, 1);
        var current = input.path("refinementMaterial").path("currentEditableValues");

        assertThat(input.path("selectedCandidate").path("candidate").path("price").asText())
            .isEqualTo("8,900원");
        assertThat(input.path("selectedCandidate").path("candidate").path("channels").path(0).asText())
            .isEqualTo("Candidate 채널");
        assertThat(current.path("price").asText()).isEqualTo("10,000원");
        assertThat(current.path("channels").path(0).asText()).isEqualTo("Seed 채널");
        assertThat(current.path("targetUsers").asText()).isEqualTo("Seed 확정 고객");
        assertThat(current.path("featureSet").path(0).asText()).isEqualTo("Seed 기능");
        assertThat(current.path("keyActivities").path(0).asText()).isEqualTo("검증 활동");
        assertThat(current.path("keyResources").path(0).asText()).isEqualTo("검증 자원");
        assertThat(current.path("keyPartners").path(0).asText()).isEqualTo("검증 파트너");
        assertThat(current.path("customerRelationships").asText()).isEqualTo("검증 관계");
        assertThat(current.has("budget_krw")).isFalse();
        assertThat(current.has("months")).isFalse();
        assertThat(current.has("team")).isFalse();
    }

    @Test
    void seedOwnsFrozenAndLegalValuesWhileMarketEvidenceStillUsesExactVersion() {
        Fixture f = fixture();
        var material = f.factory.input(41L, f.selection, f.source, 1).path("refinementMaterial");

        assertThat(material.path("frozenValues").path("conceptName").asText()).isEqualTo("Seed 사업안");
        assertThat(material.path("frozenValues").path("operatingModel").asText()).isEqualTo("Seed 운영");
        assertThat(material.path("frozenValues").has("advertisingClaims")).isFalse();
        assertThat(material.path("legalFindings").path(0).path("safeSummary").asText())
            .isEqualTo("Seed 법률 기준");
        var refs = new ArrayList<String>();
        material.path("allowedLegalRefs").forEach(value -> refs.add(value.asText()));
        assertThat(refs).containsExactly("제1조", "LAW-1", "공식 제목", "전자상거래법 제1조");
        assertThat(material.path("marketEvidence").path(0).path("id").asText()).isEqualTo("E-1");
        verify(f.versions).findByIdAndProjectIdAndKindAndDeletedAtIsNull(91L, 41L, MarketResearchRun.Kind.FULL);
        verify(f.versions).findByIdAndProjectIdAndKindAndDeletedAtIsNull(92L, 41L, MarketResearchRun.Kind.BM);
        verify(f.versions, never()).findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(anyLong(), any());
    }

    @Test
    void bmPlanRevisionMismatchIsSafeStaleBeforeTaskCreation() {
        Fixture f = fixture();
        when(f.bmPlans.current(41L)).thenReturn(new BmPlanPreparationService.PlanView(
            f.mapper.createObjectNode(), f.mapper.createObjectNode(), 4));
        assertStale(() -> f.factory.input(41L, f.selection, f.source, 1));
    }

    @Test
    void missingOrStaleExactSeedIsSafeStale() {
        Fixture f = fixture();
        when(f.seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull("seed-1")).thenReturn(Optional.empty());
        assertStale(() -> f.factory.input(41L, f.selection, f.source, 1));
    }

    @Test
    void invalidSeedContractIsSafeStaleWithoutCandidateFallback() {
        Fixture f = fixture();
        when(f.seed.getSnapshotJson()).thenReturn("{\"contract\":\"wrong\"}");
        assertStale(() -> f.factory.input(41L, f.selection, f.source, 1));
    }

    private static void assertStale(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.MODULE_INPUT_STALE));
    }

    private static Fixture fixture() {
        Fixture f = new Fixture();
        when(f.selection.getId()).thenReturn(31L);
        when(f.selection.getConceptId()).thenReturn("concept-1");
        when(f.selection.getHypothesisRevision()).thenReturn(4);
        when(f.market.getResultJson()).thenReturn("""
            {"evidence":[{"id":"E-2","value":2},{"id":"E-1","value":1}]}
            """);
        when(f.bm.getResultJson()).thenReturn("""
            {"canvas":{"cells":[{"marketEvidenceIds":["E-1"]}]},
             "bm":{"weaknesses":["채널 확인 필요"],"risks":[]}}
            """);
        when(f.versions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(91L, 41L, MarketResearchRun.Kind.FULL))
            .thenReturn(Optional.of(f.market));
        when(f.versions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(92L, 41L, MarketResearchRun.Kind.BM))
            .thenReturn(Optional.of(f.bm));
        when(f.concept.getCandidateSnapshotJson()).thenReturn("""
            {"candidateId":"candidate-1","candidate":{"price":"8,900원","channels":["Candidate 채널"]}}
            """);
        when(f.concept.getLegalReviewJson()).thenReturn("{\"safeSummary\":\"Candidate 법률\"}");
        when(f.concepts.findByIdAndProjectIdAndDeletedAtIsNull("concept-1", 41L))
            .thenReturn(Optional.of(f.concept));
        when(f.seed.getId()).thenReturn("seed-1");
        when(f.seed.getProjectId()).thenReturn(41L);
        when(f.seed.getPortfolioSelectionId()).thenReturn(31L);
        when(f.seed.getSourceType()).thenReturn("CONCEPT_PORTFOLIO_V2");
        when(f.seed.getSnapshotJson()).thenReturn(seedJson());
        when(f.seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull("seed-1"))
            .thenReturn(Optional.of(f.seed));
        var plan = f.mapper.createObjectNode();
        plan.putArray("key_activities").add("검증 활동");
        plan.putArray("key_resources").add("검증 자원");
        plan.putArray("key_partners").add("검증 파트너");
        plan.put("customer_relationship", "검증 관계");
        var constraints = f.mapper.createObjectNode();
        constraints.put("budget_krw", 1000); constraints.put("months", 3); constraints.put("team", 2);
        when(f.bmPlans.current(41L)).thenReturn(new BmPlanPreparationService.PlanView(plan, constraints, 3));
        return f;
    }

    private static String seedJson() {
        return """
            {"contract":"market-analysis-seed-snapshot-v1","schemaVersion":"2.0",
             "selectedConcept":{
               "identity":{"conceptName":"Seed 사업안","conceptDefinition":"Seed 정의","coreValue":"Seed 가치","targetUsers":"Seed 확정 고객"},
               "solution":{"featureSet":["Seed 기능"]},
               "operation":{"operatingModel":"Seed 운영","platformRole":"Seed 플랫폼","sellerRole":"판매자","providerRole":"공급자",
                 "intermediaryRole":"중개자","transactionFlow":"거래","paymentFlow":"결제","personalDataUsage":"개인정보",
                 "physicalActivities":["배송"],"partnerRequirements":["자격"],"qualificationRequirements":["면허"]}},
             "finalHypotheses":{
               "targetRegion":{"value":"대한민국"},"revenueModel":{"value":"구독"},"price":{"value":"10,000원"},
               "channels":{"value":["Seed 채널"]},"differentiators":{"value":["Seed 차별점"]},
               "preMarketSomShare":{"value":{"targetSharePercent":1}},"preMarketSom":{"value":{"amount":1000000}}},
             "legalResult":{"safeSummary":"Seed 법률 기준","officialEvidenceReferences":[
               {"lawName":"전자상거래법","articleReference":"제1조","officialIdentifier":"LAW-1","title":"공식 제목"}]}}
            """;
    }

    private static final class Fixture {
        final ObjectMapper mapper = new ObjectMapper();
        final MarketResearchVersionRepository versions = mock(MarketResearchVersionRepository.class);
        final ConceptPortfolioConceptRepository concepts = mock(ConceptPortfolioConceptRepository.class);
        final MarketAnalysisSeedSnapshotRepository seeds = mock(MarketAnalysisSeedSnapshotRepository.class);
        final BmPlanPreparationService bmPlans = mock(BmPlanPreparationService.class);
        final ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        final MarketResearchVersion market = mock(MarketResearchVersion.class);
        final MarketResearchVersion bm = mock(MarketResearchVersion.class);
        final ConceptPortfolioConcept concept = mock(ConceptPortfolioConcept.class);
        final MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        final CompletedSource source = new CompletedSource("session-1", 91L, 92L, "seed-1", 31L, 4, 3, HASH);
        final ConceptRefinementMaterialFactory factory = new ConceptRefinementMaterialFactory(
            versions, concepts, seeds, bmPlans, mapper);
    }
}
