package com.aivle.backend.document.structure;

import com.aivle.backend.common.entity.MissingFieldStatus;
import com.aivle.backend.common.entity.Priority;
import com.aivle.backend.common.entity.StructuredPlanStatus;
import com.aivle.backend.document.parsing.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public final class StructuredPlanMapper {
    private final BusinessPlanSectionCatalog catalog;

    public StructuredPlanMapper(BusinessPlanSectionCatalog catalog) {
        this.catalog = catalog;
    }

    public StructuredPlanMappingResult map(
            ParsedDocument parsedDocument,
            AiStructuredPlanResult aiResult
    ) {
        if (parsedDocument == null || aiResult == null) {
            throw new IllegalArgumentException("parsedDocument and aiResult are required");
        }

        List<String> warnings = new ArrayList<>(aiResult.warnings());
        List<StructuredPlanMappingError> errors = new ArrayList<>();
        Map<BusinessPlanSectionCode, AiStructuredPlanItem> indexedItems =
                indexItems(aiResult.items(), errors);
        Set<Integer> validBlockSequences = new HashSet<>();
        parsedDocument.blocks().forEach(block -> validBlockSequences.add(block.sequence()));

        List<StructuredPlanSectionDraft> sections = new ArrayList<>();
        List<MissingFieldDraft> missingFields = new ArrayList<>();
        int requiredCount = 0;
        int presentRequiredCount = 0;

        for (BusinessPlanSectionDefinition definition : catalog.all()) {
            if (definition.required()) {
                requiredCount++;
            }
            AiStructuredPlanItem item = indexedItems.get(definition.code());
            NormalizedItem normalized = normalizeItem(
                    definition,
                    item,
                    validBlockSequences,
                    warnings,
                    errors
            );
            sections.add(toSectionDraft(definition, normalized));
            if (definition.required() && normalized.status() == StructuredItemStatus.PRESENT) {
                presentRequiredCount++;
            }
            if (normalized.status() != StructuredItemStatus.PRESENT) {
                missingFields.add(toMissingFieldDraft(definition, normalized));
            }
        }

        int completionRate = requiredCount == 0
                ? 100
                : (presentRequiredCount * 100) / requiredCount;
        StructuredPlanStatus status = missingFields.isEmpty()
                ? StructuredPlanStatus.DRAFT
                : StructuredPlanStatus.NEEDS_INPUT;

        return new StructuredPlanMappingResult(
                sections,
                missingFields,
                completionRate,
                status,
                warnings,
                errors
        );
    }

    private Map<BusinessPlanSectionCode, AiStructuredPlanItem> indexItems(
            List<AiStructuredPlanItem> items,
            List<StructuredPlanMappingError> errors
    ) {
        EnumMap<BusinessPlanSectionCode, AiStructuredPlanItem> indexed =
                new EnumMap<>(BusinessPlanSectionCode.class);
        for (AiStructuredPlanItem item : items) {
            BusinessPlanSectionCode code;
            try {
                code = BusinessPlanSectionCode.valueOf(
                        item.sectionCode().strip().toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                errors.add(new StructuredPlanMappingError(
                        StructuredPlanMappingErrorCode.UNKNOWN_SECTION_CODE,
                        item.sectionCode(),
                        "정의되지 않은 사업계획서 섹션 코드입니다."
                ));
                continue;
            }
            if (indexed.putIfAbsent(code, item) != null) {
                errors.add(new StructuredPlanMappingError(
                        StructuredPlanMappingErrorCode.DUPLICATE_SECTION_RESULT,
                        code.name(),
                        "동일 섹션 결과가 중복되었습니다. 첫 번째 결과만 사용합니다."
                ));
            }
        }
        return indexed;
    }

    private NormalizedItem normalizeItem(
            BusinessPlanSectionDefinition definition,
            AiStructuredPlanItem item,
            Set<Integer> validBlockSequences,
            List<String> warnings,
            List<StructuredPlanMappingError> errors
    ) {
        if (item == null) {
            return new NormalizedItem(
                    StructuredItemStatus.UNKNOWN,
                    null,
                    "AI 구조화 결과에 해당 항목이 없습니다.",
                    null,
                    List.of(),
                    List.of()
            );
        }

        StructuredItemStatus status = item.status();
        if (status == StructuredItemStatus.PRESENT
                && (item.extractedContent() == null || item.extractedContent().isBlank())) {
            status = StructuredItemStatus.INVALID;
            warnings.add("PRESENT_WITHOUT_CONTENT_DOWNGRADED:" + definition.code().name());
        }
        if (item.sectionName() != null
                && !item.sectionName().isBlank()
                && !definition.displayName().equals(item.sectionName())) {
            warnings.add("SECTION_NAME_MISMATCH:" + definition.code().name());
        }

        List<Integer> validReferences = new ArrayList<>();
        for (Integer reference : item.sourceBlockReferences()) {
            if (reference != null && validBlockSequences.contains(reference)) {
                validReferences.add(reference);
            } else {
                errors.add(new StructuredPlanMappingError(
                        StructuredPlanMappingErrorCode.INVALID_SOURCE_BLOCK_REFERENCE,
                        definition.code().name(),
                        "존재하지 않는 원문 블록 참조입니다."
                ));
            }
        }
        return new NormalizedItem(
                status,
                item.extractedContent(),
                item.reason(),
                item.confidence(),
                item.evidence(),
                validReferences
        );
    }

    private StructuredPlanSectionDraft toSectionDraft(
            BusinessPlanSectionDefinition definition,
            NormalizedItem item
    ) {
        return new StructuredPlanSectionDraft(
                definition.code(),
                definition.mappedPlanSectionTypes(),
                definition.displayName(),
                item.extractedContent(),
                item.status(),
                item.reason(),
                item.confidence(),
                item.evidence(),
                item.sourceBlockReferences()
        );
    }

    private MissingFieldDraft toMissingFieldDraft(
            BusinessPlanSectionDefinition definition,
            NormalizedItem item
    ) {
        return new MissingFieldDraft(
                "SECTION_" + definition.code().name(),
                definition.displayName(),
                definition.required(),
                MissingFieldStatus.OPEN,
                missingReason(item),
                missingPriority(item.status()),
                definition.code()
        );
    }

    private String missingReason(NormalizedItem item) {
        if (item.reason() != null && !item.reason().isBlank()) {
            return item.reason();
        }
        return switch (item.status()) {
            case PARTIAL -> "필수 내용이 일부만 확인되었습니다.";
            case MISSING -> "필수 내용을 확인할 수 없습니다.";
            case INVALID -> "추출 결과가 유효하지 않습니다.";
            case UNKNOWN -> "구조화 결과를 확인할 수 없습니다.";
            case PRESENT -> "";
        };
    }

    private Priority missingPriority(StructuredItemStatus status) {
        return status == StructuredItemStatus.PARTIAL ? Priority.MEDIUM : Priority.HIGH;
    }

    private record NormalizedItem(
            StructuredItemStatus status,
            String extractedContent,
            String reason,
            java.math.BigDecimal confidence,
            List<String> evidence,
            List<Integer> sourceBlockReferences
    ) {
    }
}
