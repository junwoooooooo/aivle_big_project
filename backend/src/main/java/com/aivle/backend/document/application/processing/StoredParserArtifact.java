package com.aivle.backend.document.application.processing;

public record StoredParserArtifact(
    String storageKey,
    String storedFilename,
    long sizeBytes,
    String checksumSha256,
    String schemaVersion,
    int blockCount,
    boolean created
) {
}
