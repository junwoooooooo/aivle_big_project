package com.aivle.backend.pipeline.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.marketing.application.MarketingLegalGuard;
import com.aivle.backend.pipeline.marketing.application.MarketingResultContract;
import com.aivle.backend.pipeline.marketing.application.MarketingSourceSnapshotFactory;
import com.aivle.backend.pipeline.marketing.domain.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketingContentContractsTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sourceUsesSelectedConceptAcceptedHypothesesAndLegalResultOnly() {
        var marketSeed = MarketAnalysisSeedSnapshot.create("market-seed-1", 1L, 2L, "concept-1", "2.0",
            "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64), """
            {"selectedConcept":{"identity":{"conceptName":"Local Cart","targetUsers":"지역 상점","coreValue":"당일 연결","conceptDefinition":"지역 재고 연결"},
             "solution":{"problemScenario":"재고 폐기","solutionMechanism":"당일 매칭","featureSet":["재고 매칭"]},
             "operation":{"operatingModel":"직접 운영"}},
             "finalHypotheses":{"targetRegion":{"value":"대한민국"},"revenueModel":{"value":"구독"},"price":{"value":"월 9,900원"},
             "channels":{"value":"직접 영업"},"differentiators":{"value":"당일 연결"},
             "preMarketSomShare":{"value":{"targetSharePercent":2.5,"horizonYears":3}},
             "preMarketSom":{"value":{"amount":100000000,"currency":"KRW"}}},
             "legalResult":{"legalStatus":"IMPLEMENTABLE_WITH_CONTROLS","requiredControls":["광고 범위 고지"],
             "requiredDisclosures":["지역별 제공 범위 상이"],"prohibitedVariants":["전 지역 최저가"],"officialEvidenceReferences":[]}}
            """, 9L, Instant.EPOCH);
        Concept concept = mock(Concept.class);
        when(concept.getTitle()).thenReturn("Local Cart"); when(concept.getSummary()).thenReturn("지역 연결");
        when(concept.getCandidateJson()).thenReturn("{\"advertisingClaims\":[\"당일 연결 가능 지역 운영\"]}");
        var factory = new MarketingSourceSnapshotFactory(mapper, new SnapshotHasher(mapper));

        var first = factory.create("source-1", Instant.EPOCH, marketSeed, concept);
        var second = factory.create("source-1", Instant.EPOCH, marketSeed, concept);

        assertThat(first.hash()).isEqualTo(second.hash()).matches("sha256:[0-9a-f]{64}");
        assertThat(first.body().path("schemaVersion").asText()).isEqualTo("2.0");
        assertThat(first.body().path("allowedClaims").get(0).asText()).isEqualTo("당일 연결 가능 지역 운영");
        assertThat(first.body().path("prohibitedClaims").get(0).asText()).isEqualTo("전 지역 최저가");
        assertThat(first.body().path("communicationRequiredControls").get(0).asText()).isEqualTo("광고 범위 고지");
        assertThat(first.body().has("marketResult")).isFalse();
        assertThat(first.body().has("finalizedPlanningSnapshot")).isFalse();
    }

    @Test
    void legalGuardBlocksProhibitedClaimAndMissingDisclosure() {
        var guard = new MarketingLegalGuard(mapper);
        String source = "{\"prohibitedClaims\":[\"전 지역 최저가\"],\"requiredDisclosures\":[\"지역별 제공 범위 상이\"]}";
        var prohibited = mapper.readTree("""
            {"title":"전 지역 최저가","body":"본문","callToAction":null,"imageBrief":null,
             "legalReview":{"requiredDisclosuresApplied":["지역별 제공 범위 상이"]}}
            """);
        var missing = mapper.readTree("""
            {"title":"제목","body":"본문","callToAction":null,"imageBrief":null,
             "legalReview":{"requiredDisclosuresApplied":[]}}
            """);
        assertThatThrownBy(() -> guard.validate(source, prohibited)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard.validate(source, missing)).isInstanceOf(BusinessException.class);
    }

    @Test
    void aiResultContractIsClosed() {
        var contract = new MarketingResultContract();
        var valid = mapper.readTree("""
            {"contract":"marketing-content-result-v1","contentType":"EMAIL","title":"Hello","body":"Body","callToAction":null,"hashtags":[],"imageBrief":null,"legalReview":{"compliant":true,"warnings":[],"requiredDisclosuresApplied":[]},"artifactRefs":[]}
            """);
        contract.validate(valid, MarketingContentType.EMAIL);
        ((tools.jackson.databind.node.ObjectNode) valid).put("unexpected", true);
        assertThatThrownBy(() -> contract.validate(valid, MarketingContentType.EMAIL)).isInstanceOf(IllegalArgumentException.class);

        var withImage = mapper.readTree("""
            {"contract":"marketing-content-result-v1","contentType":"EMAIL","title":"Hello","body":"Body","callToAction":null,"hashtags":[],"imageBrief":"제품 이미지","legalReview":{"compliant":true,"warnings":[],"requiredDisclosuresApplied":[]},"artifactRefs":["ai-artifacts/00000000-0000-4000-8000-000000000001.jpg"]}
            """);
        contract.validate(withImage, MarketingContentType.EMAIL);
    }

    @Test
    void taskLifecycleSeparatesGeneratedUserAndFinalizedRevisions() {
        var content = MarketingContent.queued("content-1", 1L, "source-1", "sha256:" + "0".repeat(64),
            "{}", "{}", MarketingContentType.SOCIAL_POST, "social", "title", 7L);
        content.attachTaskRun("task-1"); content.start(); int generated = content.completeRevision();
        int edited = content.addUserRevision(); int finalized = content.finalizeContent(Instant.EPOCH);
        assertThat(MarketingContentRevision.create(content.getId(), generated, MarketingRevisionType.GENERATED,
            MarketingRevisionOrigin.AI, "{}", null).getOrigin()).isEqualTo(MarketingRevisionOrigin.AI);
        assertThat(MarketingContentRevision.create(content.getId(), edited, MarketingRevisionType.USER_EDITED,
            MarketingRevisionOrigin.USER, "{}", 7L).getOrigin()).isEqualTo(MarketingRevisionOrigin.USER);
        assertThat(MarketingContentRevision.create(content.getId(), finalized, MarketingRevisionType.FINALIZED,
            MarketingRevisionOrigin.SYSTEM, "{}", 7L).getOrigin()).isEqualTo(MarketingRevisionOrigin.SYSTEM);
        assertThat(content.getStatus()).isEqualTo(MarketingContentStatus.FINALIZED);
    }
}
