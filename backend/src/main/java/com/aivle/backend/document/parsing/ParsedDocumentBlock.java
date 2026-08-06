package com.aivle.backend.document.parsing;

public record ParsedDocumentBlock(
        ParsedBlockType blockType,
        int sequence,
        String text,
        String sourceLocation,
        Integer tableIndex,
        Integer tableRow,
        Integer tableColumn,
        Integer headingLevel
) {
    public ParsedDocumentBlock {
        if (blockType == null) {
            throw new IllegalArgumentException("blockType is required");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        text = text == null ? "" : text;
        sourceLocation = sourceLocation == null ? "" : sourceLocation;
    }

    public String blockId() {
        return "b-%06d".formatted(sequence);
    }
}
