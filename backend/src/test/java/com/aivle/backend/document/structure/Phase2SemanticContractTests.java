package com.aivle.backend.document.structure;

import com.aivle.backend.integration.ai.MockAiServiceClient;
import com.aivle.backend.integration.ai.document.DocumentStructureAiRequest;
import com.aivle.backend.integration.ai.document.DocumentStructureBlock;
import com.aivle.backend.integration.ai.document.DocumentStructureSection;
import com.aivle.backend.integration.ai.prompt.DocumentStructurePromptFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Phase2SemanticContractTests {
    private static final List<BusinessPlanSectionCode> CANONICAL_CODES = List.of(
        BusinessPlanSectionCode.BUSINESS_OVERVIEW,
        BusinessPlanSectionCode.MARKET_SIZE,
        BusinessPlanSectionCode.TARGET_CUSTOMER,
        BusinessPlanSectionCode.COMPETITIVE_ANALYSIS,
        BusinessPlanSectionCode.PRODUCT_SERVICE,
        BusinessPlanSectionCode.BUSINESS_MODEL,
        BusinessPlanSectionCode.COST_PROFITABILITY,
        BusinessPlanSectionCode.SALES_GOALS_FINANCIAL_PROJECTIONS,
        BusinessPlanSectionCode.TECHNOLOGY_PRODUCTION,
        BusinessPlanSectionCode.LEGAL_PERMITS,
        BusinessPlanSectionCode.SCHEDULE_RISK,
        BusinessPlanSectionCode.EVIDENCE_LIST
    );

    @Test
    void catalogPromptMockAndDomainShareCanonicalCodesInOrder() {
        BusinessPlanSectionCatalog catalog = new BusinessPlanSectionCatalog();
        var prompt = new DocumentStructurePromptFactory(catalog).current();
        var request = request(catalog, prompt);
        var mockResult = new MockAiServiceClient().structureDocument(request).result();

        assertThat(Arrays.asList(BusinessPlanSectionCode.values()))
            .containsExactlyElementsOf(CANONICAL_CODES);
        assertThat(catalog.all()).extracting(BusinessPlanSectionDefinition::code)
            .containsExactlyElementsOf(CANONICAL_CODES);
        assertThat(request.sections()).extracting(DocumentStructureSection::code)
            .containsExactlyElementsOf(CANONICAL_CODES.stream().map(Enum::name).toList());
        assertThat(mockResult.items()).extracting(AiStructuredPlanItem::sectionCode)
            .containsExactlyElementsOf(CANONICAL_CODES.stream().map(Enum::name).toList());
        CANONICAL_CODES.forEach(code ->
            assertThat(prompt.template()).contains(code.name()));
        assertThat(prompt.template())
            .doesNotContain("EXECUTIVE_SUMMARY", "GO_TO_MARKET", "FINANCIALS");
    }

    @Test
    void domainPreservesAllFiveStructuredStatuses() {
        assertThat(StructuredItemStatus.values()).containsExactly(
            StructuredItemStatus.PRESENT,
            StructuredItemStatus.MISSING,
            StructuredItemStatus.PARTIAL,
            StructuredItemStatus.INVALID,
            StructuredItemStatus.UNKNOWN
        );
    }

    @Test
    void openApiMatchesCanonicalCodesAndStatuses() throws IOException {
        String openApi = Files.readString(Path.of("..", "docs", "api", "openapi.yaml"));
        CANONICAL_CODES.forEach(code -> assertThat(openApi).contains("- " + code.name()));
        assertThat(openApi).contains("enum: [PRESENT, MISSING, PARTIAL, INVALID, UNKNOWN]");
        assertThat(openApi).doesNotContain(
            "- EXECUTIVE_SUMMARY",
            "- FINANCIALS"
        );
        assertThat(openApi).contains(
            "FeasibilityDimensionCode:",
            "- GO_TO_MARKET"
        );
    }

    private DocumentStructureAiRequest request(
        BusinessPlanSectionCatalog catalog,
        com.aivle.backend.integration.ai.prompt.DocumentStructurePrompt prompt
    ) {
        return new DocumentStructureAiRequest(
            1L,
            2L,
            3L,
            "docx",
            "1",
            "plan.docx",
            List.of(new DocumentStructureBlock(
                0, "PARAGRAPH", "사업 개요", "body[0]", null, null, null
            )),
            catalog.all().stream()
                .map(definition -> new DocumentStructureSection(
                    definition.code().name(),
                    definition.displayName(),
                    definition.description(),
                    definition.required(),
                    definition.allowedMissingPolicy().name(),
                    definition.aliases()
                ))
                .toList(),
            prompt.catalogVersion(),
            prompt.version(),
            prompt.template(),
            prompt.sha256()
        );
    }
}
