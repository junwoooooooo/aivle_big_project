package com.aivle.backend.document.application.processing;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DocumentParseMetadata(
    String parserName,
    String parserVersion,
    int totalCharacters,
    int totalBlocks,
    List<String> warnings,
    Instant parsedAt,
    Map<String, String> parserMetadata
) {
    public DocumentParseMetadata {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        parserMetadata = parserMetadata == null ? Map.of() : Map.copyOf(parserMetadata);
    }
}
