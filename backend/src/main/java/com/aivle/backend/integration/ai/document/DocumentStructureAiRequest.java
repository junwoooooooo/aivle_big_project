package com.aivle.backend.integration.ai.document;

import java.util.List;

public record DocumentStructureAiRequest(
    Long jobId,
    Long projectId,
    Long documentVersionId,
    String parserName,
    String parserVersion,
    String originalFileName,
    List<DocumentStructureBlock> blocks,
    List<DocumentStructureSection> sections,
    String catalogVersion,
    String promptVersion,
    String promptText,
    String requestHash
) {
    public DocumentStructureAiRequest {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
