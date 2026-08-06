package com.aivle.backend.document.parsing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ParsedDocument(
        String originalFileName,
        ParsedDocumentType documentType,
        String parserName,
        String parserVersion,
        Instant parsedAt,
        Map<String, String> parsingMetadata,
        List<ParsedDocumentBlock> blocks,
        String plainText,
        int totalCharacters,
        int totalBlocks,
        List<String> warnings
) {
    public ParsedDocument {
        originalFileName = originalFileName == null ? "" : originalFileName;
        if (documentType == null || parserName == null || parserVersion == null || parsedAt == null) {
            throw new IllegalArgumentException("document type and parser metadata are required");
        }
        parsingMetadata = parsingMetadata == null ? Map.of() : Map.copyOf(parsingMetadata);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        plainText = plainText == null ? "" : plainText;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (totalCharacters != plainText.length()) {
            throw new IllegalArgumentException("totalCharacters must match plainText length");
        }
        if (totalBlocks != blocks.size()) {
            throw new IllegalArgumentException("totalBlocks must match blocks size");
        }
    }

    public static ParsedDocument fromBlocks(
            String originalFileName,
            ParsedDocumentType documentType,
            String parserName,
            String parserVersion,
            Instant parsedAt,
            Map<String, String> parsingMetadata,
            List<ParsedDocumentBlock> blocks,
            List<String> warnings
    ) {
        List<ParsedDocumentBlock> immutableBlocks = List.copyOf(blocks);
        String plainText = immutableBlocks.stream()
                .map(ParsedDocumentBlock::text)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n"));
        return new ParsedDocument(
                originalFileName,
                documentType,
                parserName,
                parserVersion,
                parsedAt,
                parsingMetadata,
                immutableBlocks,
                plainText,
                plainText.length(),
                immutableBlocks.size(),
                warnings
        );
    }
}
