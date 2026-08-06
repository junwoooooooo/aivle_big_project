package com.aivle.backend.pipeline.legal.application;

import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefFieldCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CanonicalLegalContextAssembler {
    public static final Set<String> FIELD_KEYS = Set.of(
        "problem", "targetCustomers", "usageContext", "targetRegion", "fixedConditions",
        "prohibitedMethods", "physicalActivity", "personalData", "payment", "requiredPartners"
    );
    private final ObjectMapper mapper;

    public CanonicalLegalContextAssembler(ObjectMapper mapper) { this.mapper = mapper; }

    public Result assemble(List<IdeaBriefField> fields) {
        List<Map<String, String>> canonical = new ArrayList<>();
        for (IdeaBriefField field : fields) {
            if (!FIELD_KEYS.contains(field.getFieldKey()) || field.getFieldValue() == null
                || field.getFieldValue().isBlank()) continue;
            IdeaBriefFieldCatalog.require(field.getFieldKey());
            canonical.add(Map.of("fieldKey", field.getFieldKey(), "value", field.getFieldValue(),
                "provenance", "SOURCE_EXTRACTED"));
        }
        canonical.sort(Comparator.comparing(value -> value.get("fieldKey")));
        if (canonical.isEmpty()) throw new IllegalStateException("canonical legal context is empty");
        return new Result(mapper.writeValueAsString(canonical), mapper.writeValueAsString(Map.of(
            "canonicalFields", "SOURCE_EXTRACTED", "candidateActivities", "DERIVED_CONTEXT")));
    }

    public record Result(String contextJson, String provenanceJson) {}
}
