package com.aivle.backend.pipeline.marketing;

import static org.junit.jupiter.api.Assertions.*;

import com.aivle.backend.pipeline.marketing.application.*;
import com.aivle.backend.pipeline.marketing.domain.*;
import com.aivle.backend.pipeline.planning.domain.FinalizedPlanningSnapshot;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketingContentContractsTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sourceSnapshotUsesOnlyFinalizedPlanningFieldsAndHasStableHash() {
        var factory = new MarketingSourceSnapshotFactory(mapper, new SnapshotHasher(mapper));
        var finalized = FinalizedPlanningSnapshot.create("plan-1", 1L, "planning-1", "selection-1", 1, null,
            "final", """
            {"planning":{"finalConcept":{"conceptName":"Local Cart","problem":"Waste","positioning":"Local first","competitorDifferentiators":["fresh"]},"finalTarget":"Solo","finalValueProposition":"Right amount","finalFeatures":["split"],"finalPricingRevenueHypothesis":"fee","finalChannels":["app"]},"legalControls":{"allowedClaims":["same day"],"prohibitedExpressions":["always cheapest"],"requiredDisclosures":["area varies"]},"ignoredMarketDatabase":{"secret":"must not flow"}}
            """, "sha256:" + "1".repeat(64), 9L, Instant.EPOCH);
        var first = factory.create(finalized); var second = factory.create(finalized);
        MarketingSourceSnapshot typed = factory.map(finalized);
        assertEquals(first.path("sourceSnapshotHash").asText(), second.path("sourceSnapshotHash").asText());
        assertTrue(factory.matches(finalized, typed.sourceSnapshotHash()));
        assertFalse(factory.matches(finalized, "sha256:" + "0".repeat(64)));
        assertEquals(List.of("split"), typed.keyFeatures());
        assertEquals(13, first.size());
        assertFalse(first.has("ignoredMarketDatabase"));
        assertEquals("Local Cart", first.path("conceptName").asText());
    }

    @Test
    void aiResultContractIsClosed() {
        var contract = new MarketingResultContract();
        var valid = mapper.readTree("""
            {"contract":"marketing-content-result-v1","contentType":"EMAIL","title":"Hello","body":"Body","callToAction":null,"hashtags":[],"imageBrief":null,"legalReview":{"compliant":true,"warnings":[],"requiredDisclosuresApplied":[]},"artifactRefs":[]}
            """);
        assertDoesNotThrow(() -> contract.validate(valid, MarketingContentType.EMAIL));
        ((tools.jackson.databind.node.ObjectNode) valid).put("unexpected", true);
        assertThrows(IllegalArgumentException.class, () -> contract.validate(valid, MarketingContentType.EMAIL));
    }

    @Test
    void taskLifecycleSeparatesGeneratedUserAndFinalizedRevisions() {
        var content = MarketingContent.queued("content-1",1L,"plan-1","sha256:"+"0".repeat(64),"{}","{}",MarketingContentType.SOCIAL_POST,"social","title",7L);
        content.attachTaskRun("task-1"); content.start(); int generated=content.completeRevision(); int edited=content.addUserRevision(); int finalized=content.finalizeContent(Instant.EPOCH);
        var ai = MarketingContentRevision.create(content.getId(),generated,MarketingRevisionType.GENERATED,MarketingRevisionOrigin.AI,"{}",null);
        var user = MarketingContentRevision.create(content.getId(),edited,MarketingRevisionType.USER_EDITED,MarketingRevisionOrigin.USER,"{}",7L);
        var system = MarketingContentRevision.create(content.getId(),finalized,MarketingRevisionType.FINALIZED,MarketingRevisionOrigin.SYSTEM,"{}",7L);
        assertAll(() -> assertEquals(MarketingRevisionOrigin.AI,ai.getOrigin()), () -> assertEquals(MarketingRevisionOrigin.USER,user.getOrigin()),
            () -> assertEquals(MarketingRevisionOrigin.SYSTEM,system.getOrigin()), () -> assertEquals(MarketingContentStatus.FINALIZED,content.getStatus()));
    }
}
