package com.aivle.backend.pipeline.techops;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.techops.application.*;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputPreparation;
import com.aivle.backend.pipeline.techops.domain.TechOpsEvidenceReference;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TechOpsPreparationContractsTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preparationReusesTrustedConceptAndSeparatesEvidenceFromHypotheses() {
        var source = MarketAnalysisSeedSnapshot.create("seed-1", 1L, 2L, "concept-1", "2.0",
            "sha256:"+"a".repeat(64), "sha256:"+"b".repeat(64), """
            {"originalSeed":{"fields":{"ownedPersonnel":{"value":"[{\\"role\\":\\"개발\\",\\"count\\":2}]","source":"USER_INPUT"}}},
             "selectedConcept":{"identity":{"conceptDefinition":"지역 재고 연결"},
             "solution":{"solutionMechanism":"당일 재고 매칭","featureSet":["재고 조회"]},
             "operation":{"operatingModel":"플랫폼 운영","partnerModel":"지역 상점 제휴","partnerRequirements":["상점 계약"],"qualificationRequirements":[]}},
             "legalResult":{"requiredControls":["광고 범위 고지"]}}
            """, 7L, Instant.EPOCH);
        var initial = new TechOpsPreparationFactory(mapper).create(source);
        assertThat(initial.requiredFacts().path("productServiceSpecification").path("readOnly").asBoolean()).isFalse();
        assertThat(initial.requiredFacts().path("productServiceSpecification").path("decision").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(initial.requiredFacts().path("ownedPersonnel").path("value").get(0).path("count").asInt()).isEqualTo(2);
        assertThat(initial.proposalDecisions().path("deliveryOrProductionMethod").path("source").asText()).isEqualTo("CONCEPT_GENERATED");
        assertThat(initial.proposalDecisions().path("technicalSupplyOperationalConstraints").path("proposalValue").toString())
            .contains("광고 범위 고지", "상점 계약");
        assertThat(initial.requiredFacts().has("evidenceReferences")).isFalse();
    }

    @Test
    void preparationFactoryPreservesPortfolioSolutionOperationAndLegalSources() {
        var source = MarketAnalysisSeedSnapshot.createPortfolio("seed-v2", 1L, 22L, "concept-v2", "legal-v2", "2.0",
            "sha256:" + "a".repeat(64), "sha256:" + "b".repeat(64), """
            {"originalSeed":{"fields":{}},
             "selectedConcept":{"identity":{"conceptDefinition":"재료 기반 요리 서비스"},
             "solution":{"solutionMechanism":"보유 재료로 요리법 추천","featureSet":["재료 검색","요리법 공유"]},
             "operation":{"operatingModel":"온라인 커뮤니티 운영","partnerModel":"식품사 제휴",
               "partnerRequirements":["제휴 데이터 계약"],"qualificationRequirements":["콘텐츠 검수"]}},
             "legalResult":{"requiredControls":["광고성 표시"]}}
            """, 7L, Instant.EPOCH);

        var initial = new TechOpsPreparationFactory(mapper).create(source);

        assertThat(initial.requiredFacts().path("productServiceSpecification").path("value").path("summary").asText())
            .isEqualTo("보유 재료로 요리법 추천");
        assertThat(initial.requiredFacts().path("productServiceSpecification").path("value").path("features"))
            .hasSize(2);
        assertThat(initial.proposalDecisions().path("deliveryOrProductionMethod").path("proposalValue").toString())
            .contains("온라인 커뮤니티 운영", "식품사 제휴");
        assertThat(initial.proposalDecisions().path("technicalSupplyOperationalConstraints").path("proposalValue").toString())
            .contains("광고성 표시", "제휴 데이터 계약", "콘텐츠 검수");
    }

    @Test
    void readinessRequiresAllFactsAndAcceptedDecisions() {
        var facts=mapper.createObjectNode();
        TechOpsPreparationFactory.REQUIRED_FACT_KEYS.forEach(key -> facts.putObject(key).put("value", key).put("decision", "LOCKED"));
        var decisions=mapper.createObjectNode();
        TechOpsPreparationFactory.PROPOSAL_KEYS.forEach(key -> decisions.putObject(key).put("decision", "ACCEPTED").put("finalValue", key));
        assertThat(new TechOpsReadiness().missing(facts, decisions)).isEmpty();
        ((tools.jackson.databind.node.ObjectNode) decisions.path("expectedMonthlyThroughputOrSales"))
            .put("decision", "REJECTED").putNull("finalValue");
        assertThat(new TechOpsReadiness().missing(facts, decisions)).containsExactly("expectedMonthlyThroughputOrSales");
    }

    @Test
    void snapshotHashIsStableAndCarriesOnlyImmutableArtifactMetadata() {
        String facts="""
            {"productServiceSpecification":{"value":{"summary":"서비스","features":["기능"]}},
             "targetLaunchDate":{"value":"2027-03-01"},"ownedPersonnel":{"value":[{"role":"개발","count":2}]},
             "ownedAssetsAndFacilities":{"value":["클라우드"]},"fixedOperatingCost":{"value":{"amount":1,"currency":"KRW"}},
             "initialInvestment":{"value":{"amount":2,"currency":"KRW"}},
             "threeYearTargets":{"value":{"metric":"customerCount","unit":"명","years":[{"year":1,"value":100},{"year":2,"value":500},{"year":3,"value":1500}]}}}
            """;
        String decisions="""
            {"deliveryOrProductionMethod":{"finalValue":{"method":"직접 개발"},"source":"USER_INPUT","decision":"USER_EDITED_ACCEPTED","proposalVersion":1},
             "expectedMonthlyThroughputOrSales":{"finalValue":{"amount":10,"unit":"건"},"source":"USER_INPUT","decision":"USER_EDITED_ACCEPTED","proposalVersion":1},
             "technicalSupplyOperationalConstraints":{"finalValue":["점검"],"source":"USER_INPUT","decision":"USER_EDITED_ACCEPTED","proposalVersion":1}}
            """;
        var preparation=TechOpsInputPreparation.create("prep-1",1L,"seed-1","sha256:"+"a".repeat(64),facts,decisions,7L);
        var factory=new TechOpsInputSnapshotFactory(mapper,new SnapshotHasher(mapper));
        var artifact = ProjectEvidenceArtifact.create("artifact-1", 1L, StorageType.LOCAL,
            "projects/1/evidence/secret.pdf", "견적서.pdf", "uuid.pdf", "application/pdf", 120L,
            "sha256:" + "c".repeat(64), 7L);
        var reference = TechOpsEvidenceReference.create("evidence-1", "prep-1", 1L, "QUOTE",
            "견적서.pdf", "artifact-1", "공급사 견적", 7L);
        var first=factory.create("snapshot-1",Instant.EPOCH,preparation,List.of(reference), java.util.Map.of("artifact-1", artifact));
        var second=factory.create("snapshot-1",Instant.EPOCH,preparation,List.of(reference), java.util.Map.of("artifact-1", artifact));
        assertThat(first.hash()).isEqualTo(second.hash()).matches("sha256:[0-9a-f]{64}");
        var snapshotEvidence = first.body().path("evidenceReferences").get(0);
        assertThat(snapshotEvidence.path("artifactId").asText()).isEqualTo("artifact-1");
        assertThat(snapshotEvidence.path("originalFilename").asText()).isEqualTo("견적서.pdf");
        assertThat(snapshotEvidence.path("sha256").asText()).isEqualTo("sha256:" + "c".repeat(64));
        assertThat(snapshotEvidence.toString()).doesNotContain("storageKey", "secret.pdf", "projects/1");
        assertThat(first.body().path("requiredDecisions").path("deliveryOrProductionMethod").path("decision").asText())
            .isEqualTo("USER_EDITED_ACCEPTED");
    }
}
