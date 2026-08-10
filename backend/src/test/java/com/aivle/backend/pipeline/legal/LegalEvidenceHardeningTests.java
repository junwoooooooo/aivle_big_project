package com.aivle.backend.pipeline.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.concept.api.ConceptFactoryApiModels.EvidenceView;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
import com.aivle.backend.pipeline.legal.application.CanonicalLegalContextAssembler;
import com.aivle.backend.pipeline.legal.domain.LegalContextPack;
import com.aivle.backend.pipeline.legal.domain.LegalEvidence;
import com.aivle.backend.project.entity.Project;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LegalEvidenceHardeningTests {
    private static final String HASH = "sha256:" + "a".repeat(64);

    @Test
    void mapsOnlyLockedUserExternalFactsAndLeavesConceptDesignToFactPattern() {
        IdeaBriefField problem = field("problem", "예약 확인 업무");
        IdeaBriefField targetRegion = field("targetRegion", "대한민국");
        IdeaBriefField nonexistentIndustry = field("industry", "예약업");
        IdeaBriefField nonexistentPlatformRole = field("platformRole", "중개자");
        CanonicalLegalContextAssembler assembler = new CanonicalLegalContextAssembler(new ObjectMapper());

        var result = assembler.assemble(List.of(problem, targetRegion, nonexistentIndustry, nonexistentPlatformRole));

        assertThat(result.contextJson()).contains("fixedJurisdiction", "대한민국", "USER_INPUT", "LOCKED")
            .doesNotContain("problem", "industry", "platformRole");
        assertThat(result.provenanceJson()).contains("USER_INPUT_LOCKED", "CONCEPT_GENERATED");
    }

    @Test
    void conceptLegalReviewDoesNotRequireAnExternalUserFact() {
        CanonicalLegalContextAssembler assembler = new CanonicalLegalContextAssembler(new ObjectMapper());

        var result = assembler.assemble(List.of(field("problem", "예약 확인 업무")));

        assertThat(result.contextJson()).isEqualTo("[]");
    }

    @Test
    void officialProvisionEvidencePersistsReproducibilityMetadataAndRejectsHomepagePlaceholder() {
        Project project = Project.create(null, "project", null, null);
        LegalContextPack pack = LegalContextPack.ready(project, "brief", HASH, "[]", "{}", "legal-registry-v1");
        LocalDateTime retrievedAt = LocalDateTime.of(2026, 8, 7, 12, 0);
        LegalEvidence evidence = LegalEvidence.officialLaw(pack, "LAW-100", "MST-100", "개인정보 보호법",
            "제30조", "개인정보 처리방침", "https://www.law.go.kr/법령/개인정보보호법",
            "20250101", "20250313", retrievedAt, "sha256:" + "b".repeat(64),
            "처리방침을 수립하고 공개해야 합니다.", "sha256:" + "c".repeat(64), "legal-registry-v1");

        assertThat(evidence.getSourceType()).isEqualTo("OFFICIAL_LAW");
        assertThat(evidence.getLawId()).isEqualTo("LAW-100");
        assertThat(evidence.getArticleReference()).isEqualTo("제30조");
        assertThat(evidence.getEffectiveDate()).isEqualTo("20250313");
        assertThat(evidence.getRetrievedAt()).isEqualTo(retrievedAt);
        assertThat(evidence.getContentHash()).startsWith("sha256:");
        assertThat(evidence.getQueryKey()).startsWith("sha256:");

        assertThatThrownBy(() -> LegalEvidence.officialLaw(pack, null, "MST-1", "법령", "제1조", "목적",
            "https://www.law.go.kr/", null, null, retrievedAt, "sha256:" + "b".repeat(64),
            "요약", "sha256:" + "c".repeat(64), "legal-registry-v1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userEvidenceResponseContainsSourceMetadataButNoProviderBodyOrProvisionText() {
        EvidenceView response = new EvidenceView("OFFICIAL_LAW", "LAW-100", "개인정보 보호법", "제30조",
            "개인정보 처리방침", "20250313", LocalDateTime.of(2026, 8, 7, 12, 0).toInstant(ZoneOffset.UTC),
            "https://www.law.go.kr/법령/개인정보보호법");
        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).contains("lawName", "articleReference", "effectiveDate", "officialSourceUri")
            .doesNotContain("providerBody", "boundedProvisionSummary", "prompt", "authorization");
    }

    private IdeaBriefField field(String key, String value) {
        IdeaBriefField field = mock(IdeaBriefField.class);
        when(field.getFieldKey()).thenReturn(key);
        when(field.getFieldValue()).thenReturn(value);
        when(field.getDecisionState()).thenReturn(IdeaDecisionState.LOCKED);
        when(field.getProvenance()).thenReturn(IdeaFieldProvenance.USER_INPUT);
        return field;
    }
}
