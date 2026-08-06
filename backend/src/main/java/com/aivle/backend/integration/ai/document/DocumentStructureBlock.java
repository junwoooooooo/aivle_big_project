package com.aivle.backend.integration.ai.document;

public record DocumentStructureBlock(
    int sequence,
    String blockType,
    String text,
    String sourceLocation,
    Integer headingLevel,
    Integer tableRow,
    Integer tableColumn
) {
}
