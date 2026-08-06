package com.aivle.backend.integration.ai.document;

import java.util.List;

public record DocumentStructureSection(
    String code,
    String displayName,
    String description,
    boolean required,
    String allowedMissingPolicy,
    List<String> aliases
) {
    public DocumentStructureSection {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
