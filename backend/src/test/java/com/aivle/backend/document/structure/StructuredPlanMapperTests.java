package com.aivle.backend.document.structure;

import com.aivle.backend.common.entity.MissingFieldStatus;
import com.aivle.backend.common.entity.Priority;
import com.aivle.backend.common.entity.StructuredPlanStatus;
import com.aivle.backend.document.parsing.ParsedBlockType;
import com.aivle.backend.document.parsing.ParsedDocument;
import com.aivle.backend.document.parsing.ParsedDocumentBlock;
import com.aivle.backend.document.parsing.ParsedDocumentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredPlanMapperTests {
    private final BusinessPlanSectionCatalog catalog = new BusinessPlanSectionCatalog();
    private final StructuredPlanMapper mapper = new StructuredPlanMapper(catalog);

    @Test
    void mapsAllPresentItemsToTwelveSectionDrafts() {
        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(allPresentItems()));

        assertThat(result.sectionDrafts()).hasSize(12);
        assertThat(result.missingFieldDrafts()).isEmpty();
    }

    @Test
    void createsMissingDraftForAbsentRequiredItem() {
        List<AiStructuredPlanItem> items = new ArrayList<>(allPresentItems());
        items.remove(0);

        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(items));

        assertThat(result.missingFieldDrafts()).extracting(MissingFieldDraft::sectionCode)
                .containsExactly(BusinessPlanSectionCode.BUSINESS_OVERVIEW);
    }

    @Test
    void treatsPartialItemAsIncomplete() {
        List<AiStructuredPlanItem> items = replaceStatus(
                BusinessPlanSectionCode.MARKET_SIZE,
                StructuredItemStatus.PARTIAL,
                "시장 수치가 일부만 있습니다."
        );

        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(items));

        MissingFieldDraft missing = result.missingFieldDrafts().get(0);
        assertThat(missing.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(missing.reason()).isEqualTo("시장 수치가 일부만 있습니다.");
    }

    @Test
    void reportsUnknownSectionCodeWithoutUncheckedCasting() {
        List<AiStructuredPlanItem> items = new ArrayList<>(allPresentItems());
        items.add(item("NOT_A_SECTION", StructuredItemStatus.PRESENT, "알 수 없음"));

        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(items));

        assertThat(result.mappingErrors()).extracting(StructuredPlanMappingError::code)
                .contains(StructuredPlanMappingErrorCode.UNKNOWN_SECTION_CODE);
    }

    @Test
    void reportsDuplicateAndUsesFirstResult() {
        List<AiStructuredPlanItem> items = new ArrayList<>(allPresentItems());
        items.add(item(
                BusinessPlanSectionCode.BUSINESS_OVERVIEW.name(),
                StructuredItemStatus.MISSING,
                null
        ));

        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(items));

        assertThat(result.mappingErrors()).extracting(StructuredPlanMappingError::code)
                .contains(StructuredPlanMappingErrorCode.DUPLICATE_SECTION_RESULT);
        assertThat(result.sectionDrafts().get(0).status()).isEqualTo(StructuredItemStatus.PRESENT);
    }

    @Test
    void completionRateCountsOnlyPresentRequiredSections() {
        List<AiStructuredPlanItem> items = replaceStatus(
                BusinessPlanSectionCode.EVIDENCE_LIST,
                StructuredItemStatus.MISSING,
                "근거가 없습니다."
        );

        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(items));

        assertThat(result.completionRate()).isEqualTo(91);
    }

    @Test
    void statusIsNeedsInputWhenRequiredSectionIsIncomplete() {
        StructuredPlanMappingResult result = mapper.map(
                parsedDocument(),
                result(replaceStatus(
                        BusinessPlanSectionCode.LEGAL_PERMITS,
                        StructuredItemStatus.UNKNOWN,
                        null
                ))
        );

        assertThat(result.structuredPlanStatus()).isEqualTo(StructuredPlanStatus.NEEDS_INPUT);
    }

    @Test
    void missingFieldUsesOpenStatusAndCanonicalAssociation() {
        StructuredPlanMappingResult result = mapper.map(
                parsedDocument(),
                result(replaceStatus(
                        BusinessPlanSectionCode.TARGET_CUSTOMER,
                        StructuredItemStatus.MISSING,
                        null
                ))
        );

        MissingFieldDraft missing = result.missingFieldDrafts().get(0);
        assertThat(missing.status()).isEqualTo(MissingFieldStatus.OPEN);
        assertThat(missing.required()).isTrue();
        assertThat(missing.fieldCode()).isEqualTo("SECTION_TARGET_CUSTOMER");
        assertThat(missing.sectionCode()).isEqualTo(BusinessPlanSectionCode.TARGET_CUSTOMER);
    }

    @Test
    void mapperNeverCreatesConfirmedStatus() {
        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(allPresentItems()));

        assertThat(result.structuredPlanStatus()).isEqualTo(StructuredPlanStatus.DRAFT);
        assertThat(result.structuredPlanStatus()).isNotEqualTo(StructuredPlanStatus.CONFIRMED);
    }

    @Test
    void preservesKoreanExtractedContent() {
        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(allPresentItems()));

        assertThat(result.sectionDrafts().get(0).extractedContent())
                .isEqualTo("사업 개요 추출 내용");
    }

    @Test
    void keepsAbsentConfidenceAndEvidenceAbsentInsteadOfInventingValues() {
        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(allPresentItems()));

        StructuredPlanSectionDraft section = result.sectionDrafts().get(0);
        assertThat(section.confidence()).isNull();
        assertThat(section.evidence()).isEmpty();
        assertThat(section.sourceBlockReferences()).isEmpty();
    }

    @Test
    void reportsAndRemovesInvalidSourceBlockReference() {
        List<AiStructuredPlanItem> items = new ArrayList<>(allPresentItems());
        BusinessPlanSectionDefinition definition =
                catalog.require(BusinessPlanSectionCode.BUSINESS_OVERVIEW);
        items.set(0, new AiStructuredPlanItem(
                definition.code().name(),
                definition.displayName(),
                StructuredItemStatus.PRESENT,
                "사업 개요 추출 내용",
                "",
                null,
                List.of("근거"),
                List.of(1, 999)
        ));

        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(items));

        assertThat(result.sectionDrafts().get(0).sourceBlockReferences()).containsExactly(1);
        assertThat(result.mappingErrors()).extracting(StructuredPlanMappingError::code)
                .contains(StructuredPlanMappingErrorCode.INVALID_SOURCE_BLOCK_REFERENCE);
    }

    @Test
    void downgradesPresentItemWithoutContentToInvalid() {
        List<AiStructuredPlanItem> items = new ArrayList<>(allPresentItems());
        BusinessPlanSectionDefinition definition =
                catalog.require(BusinessPlanSectionCode.BUSINESS_OVERVIEW);
        items.set(0, new AiStructuredPlanItem(
                definition.code().name(),
                definition.displayName(),
                StructuredItemStatus.PRESENT,
                " ",
                "",
                null,
                List.of(),
                List.of()
        ));

        StructuredPlanMappingResult result = mapper.map(parsedDocument(), result(items));

        assertThat(result.sectionDrafts().get(0).status()).isEqualTo(StructuredItemStatus.INVALID);
        assertThat(result.missingFieldDrafts()).hasSize(1);
        assertThat(result.warnings()).contains(
                "PRESENT_WITHOUT_CONTENT_DOWNGRADED:BUSINESS_OVERVIEW"
        );
    }

    private List<AiStructuredPlanItem> allPresentItems() {
        return catalog.all().stream()
                .map(definition -> new AiStructuredPlanItem(
                        definition.code().name(),
                        definition.displayName(),
                        StructuredItemStatus.PRESENT,
                        definition.displayName() + " 추출 내용",
                        "",
                        null,
                        List.of(),
                        List.of()
                ))
                .toList();
    }

    private List<AiStructuredPlanItem> replaceStatus(
            BusinessPlanSectionCode code,
            StructuredItemStatus status,
            String reason
    ) {
        List<AiStructuredPlanItem> items = new ArrayList<>(allPresentItems());
        int index = code.ordinal();
        BusinessPlanSectionDefinition definition = catalog.require(code);
        items.set(index, new AiStructuredPlanItem(
                code.name(),
                definition.displayName(),
                status,
                status == StructuredItemStatus.PRESENT ? "추출 내용" : null,
                reason,
                null,
                List.of(),
                List.of()
        ));
        return items;
    }

    private AiStructuredPlanItem item(
            String code,
            StructuredItemStatus status,
            String content
    ) {
        return new AiStructuredPlanItem(
                code,
                code,
                status,
                content,
                null,
                BigDecimal.ONE,
                List.of(),
                List.of()
        );
    }

    private AiStructuredPlanResult result(List<AiStructuredPlanItem> items) {
        return new AiStructuredPlanResult(
                "test-provider",
                "test-model",
                "prompt-v1",
                "parser-v1",
                items,
                null,
                List.of()
        );
    }

    private ParsedDocument parsedDocument() {
        return ParsedDocument.fromBlocks(
                "business-plan.docx",
                ParsedDocumentType.DOCX,
                "test-parser",
                "1",
                Instant.parse("2026-07-23T00:00:00Z"),
                Map.of(),
                List.of(new ParsedDocumentBlock(
                        ParsedBlockType.PARAGRAPH,
                        1,
                        "사업 개요 원문",
                        "body/paragraph[1]",
                        null,
                        null,
                        null,
                        null
                )),
                List.of()
        );
    }
}
