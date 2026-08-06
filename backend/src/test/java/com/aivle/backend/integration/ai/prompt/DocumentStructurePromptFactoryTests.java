package com.aivle.backend.integration.ai.prompt;

import com.aivle.backend.document.structure.BusinessPlanSectionCatalog;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStructurePromptFactoryTests {
    private final BusinessPlanSectionCatalog catalog = new BusinessPlanSectionCatalog();
    private final DocumentStructurePrompt prompt =
        new DocumentStructurePromptFactory(catalog).current();

    @Test
    void promptContainsExactlyAllTwelveCanonicalCodes() {
        assertThat(catalog.all()).hasSize(12);
        for (BusinessPlanSectionCode code : BusinessPlanSectionCode.values()) {
            assertThat(occurrences(prompt.template(), code.name())).isEqualTo(1);
        }
    }

    @Test
    void promptVersionAndCatalogVersionAreFixed() {
        assertThat(prompt.version()).isEqualTo("business-plan-structure-v1");
        assertThat(prompt.catalogVersion()).isEqualTo("business-plan-sections-v1");
    }

    @Test
    void promptRequiresJsonOnlyAndAllMissingSections() {
        assertThat(prompt.template())
            .contains("JSON object 외의 설명")
            .contains("누락 항목도 반드시 반환")
            .contains("정확히 12개 item");
    }

    @Test
    void promptProhibitsInventedContentAndOverstatement() {
        assertThat(prompt.template())
            .contains("원문에 없는 내용을 생성하지 마세요")
            .contains("법률·재무 사실을 추측하거나 과장하지 마세요");
    }

    @Test
    void promptHashIsDeterministic() {
        DocumentStructurePrompt rebuilt =
            new DocumentStructurePromptFactory(new BusinessPlanSectionCatalog()).current();
        assertThat(prompt.sha256()).hasSize(64);
        assertThat(rebuilt.sha256()).isEqualTo(prompt.sha256());
    }

    private int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }
}
