package com.aivle.backend.taskrun.contract;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import tools.jackson.databind.ObjectMapper;

public record LegalSourcePipelineInput(
    String mode,
    List<String> rerunCategories,
    List<Map<String, Object>> confirmedFacts,
    String registryVersion,
    String promptVersion,
    String sourceSchemaVersion,
    List<TextContent> textContents
) {
    public record TextContent(String contentKey, String contentType, String language, int totalCharacters,
        String contentHash, List<TextChunk> chunks) { }
    public record TextChunk(int index, String text, int characterCount, String chunkHash) { }

    public static LegalSourcePipelineInput create(String text, String contentKey, String mode,
            List<String> rerunCategories, List<Map<String, Object>> confirmedFacts, String registryVersion,
            String promptVersion, String sourceSchemaVersion) {
        if (text == null || text.isBlank() || contentKey == null || contentKey.isBlank()) {
            throw new IllegalArgumentException("Legal source input text and contentKey are required");
        }
        int characters = text.codePointCount(0, text.length());
        String hash = hash(text);
        List<TextChunk> chunks = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int chunkCharacters = Math.min(16_000, text.codePointCount(offset, text.length()));
            int end = text.offsetByCodePoints(offset, chunkCharacters);
            String chunkText = text.substring(offset, end);
            chunks.add(new TextChunk(chunks.size(), chunkText, chunkCharacters, hash(chunkText)));
            offset = end;
        }
        TextContent content = new TextContent(contentKey, "TEXT", "ko-KR", characters, hash, List.copyOf(chunks));
        return new LegalSourcePipelineInput(mode, List.copyOf(rerunCategories), List.copyOf(confirmedFacts),
            registryVersion, promptVersion, sourceSchemaVersion, List.of(content));
    }

    public String toJson(ObjectMapper mapper) {
        return mapper.writeValueAsString(this);
    }

    private static String hash(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
