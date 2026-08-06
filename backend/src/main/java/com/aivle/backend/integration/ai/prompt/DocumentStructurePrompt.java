package com.aivle.backend.integration.ai.prompt;

public record DocumentStructurePrompt(
    String version,
    String catalogVersion,
    String template,
    String sha256
) {
}
