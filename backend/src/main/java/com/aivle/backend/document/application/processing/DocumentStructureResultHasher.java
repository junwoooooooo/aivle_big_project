package com.aivle.backend.document.application.processing;

import com.aivle.backend.document.structure.AiStructuredPlanItem;
import com.aivle.backend.document.structure.AiStructuredPlanResult;
import com.aivle.backend.document.structure.BusinessPlanSectionCatalog;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
public class DocumentStructureResultHasher {
    private final BusinessPlanSectionCatalog catalog;
    private final ObjectMapper objectMapper;

    public DocumentStructureResultHasher(
        BusinessPlanSectionCatalog catalog,
        ObjectMapper objectMapper
    ) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    public AiStructuredPlanResult withCanonicalHash(AiStructuredPlanResult result) {
        List<AiStructuredPlanItem> ordered = result.items().stream()
            .sorted(Comparator.comparingInt(item -> sequence(item.sectionCode())))
            .toList();
        String hash = sha256(json(new CanonicalResult(ordered.stream()
            .map(CanonicalItem::from)
            .toList())));
        return new AiStructuredPlanResult(
            result.provider(),
            result.model(),
            result.promptVersion(),
            result.parserVersion(),
            ordered,
            hash,
            result.warnings()
        );
    }

    private BusinessPlanSectionCode parseCode(String code) {
        try {
            return BusinessPlanSectionCode.valueOf(code);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private int sequence(String code) {
        BusinessPlanSectionCode parsed = parseCode(code);
        return parsed == null
            ? Integer.MAX_VALUE
            : catalog.require(parsed).sequence();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("typed AI result cannot be serialized", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record CanonicalResult(List<CanonicalItem> items) {
    }

    private record CanonicalItem(
        String sectionCode,
        String sectionName,
        String status,
        String extractedContent,
        String reason,
        java.math.BigDecimal confidence,
        List<String> evidence,
        List<Integer> sourceBlockReferences
    ) {
        private static CanonicalItem from(AiStructuredPlanItem item) {
            return new CanonicalItem(
                item.sectionCode(),
                item.sectionName(),
                item.status().name(),
                item.extractedContent(),
                item.reason(),
                item.confidence(),
                item.evidence(),
                item.sourceBlockReferences()
            );
        }
    }
}
