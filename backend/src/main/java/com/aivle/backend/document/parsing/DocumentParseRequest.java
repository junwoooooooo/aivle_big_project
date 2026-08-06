package com.aivle.backend.document.parsing;

import java.util.Map;

public record DocumentParseRequest(
        String originalFileName,
        String declaredContentType,
        Long declaredSizeBytes,
        Map<String, String> metadata
) {
    public DocumentParseRequest {
        originalFileName = originalFileName == null ? "" : originalFileName;
        declaredContentType = declaredContentType == null ? "" : declaredContentType;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static DocumentParseRequest of(String originalFileName, String declaredContentType) {
        return new DocumentParseRequest(originalFileName, declaredContentType, null, Map.of());
    }
}
