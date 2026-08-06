package com.aivle.backend.document.structure;

import java.util.List;

public record AiStructuredPlanResult(
        String provider,
        String model,
        String promptVersion,
        String parserVersion,
        List<AiStructuredPlanItem> items,
        String rawResultHash,
        List<String> warnings
) {
    public AiStructuredPlanResult {
        items = items == null ? List.of() : List.copyOf(items);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
