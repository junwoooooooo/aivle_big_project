package com.aivle.backend.document.application.processing;

import java.util.Arrays;

public record ParserArtifactPayload(
    byte[] bytes,
    String checksumSha256,
    int blockCount,
    int totalCharacters,
    String schemaVersion
) {
    public ParserArtifactPayload {
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
