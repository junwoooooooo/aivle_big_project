package com.aivle.backend.file.object;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ObjectKeyGenerator {
    public String documentSource(
        long projectId,
        long documentId,
        long documentVersionId,
        String extension
    ) {
        if (!"docx".equals(extension)) {
            throw new IllegalArgumentException(
                "business plan source must be docx"
            );
        }
        return "projects/" + projectId
            + "/documents/" + documentId
            + "/versions/" + documentVersionId
            + "/source/" + UUID.randomUUID() + ".docx";
    }

    public String parserArtifactTemporary(
        long projectId,
        long documentId,
        long documentVersionId
    ) {
        return "projects/" + projectId
            + "/documents/" + documentId
            + "/versions/" + documentVersionId
            + "/parser/tmp/" + UUID.randomUUID() + ".json";
    }

    public String parserArtifact(
        long projectId,
        long documentId,
        long documentVersionId,
        String parserVersion,
        String checksumSha256
    ) {
        String safeParserVersion = parserVersion.replaceAll(
            "[^A-Za-z0-9._-]",
            "_"
        );
        if (!checksumSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                "parser artifact checksum must be lowercase SHA-256"
            );
        }
        return "projects/" + projectId
            + "/documents/" + documentId
            + "/versions/" + documentVersionId
            + "/parser/" + safeParserVersion
            + "/" + checksumSha256 + ".json";
    }

    public String aiArtifactJson() {
        return "ai-artifacts/" + UUID.randomUUID() + ".json";
    }

    public String aiArtifactImage(String extension) {
        if (!extension.matches("png|jpg|jpeg|webp")) {
            throw new IllegalArgumentException("unsupported image extension");
        }
        return "ai-artifacts/" + UUID.randomUUID() + "." + extension;
    }

    public String projectEvidence(long projectId, String artifactId, String extension) {
        if (artifactId == null || !artifactId.matches("[0-9a-f-]{36}")
                || extension == null || !extension.matches("[a-z0-9]{2,5}")) {
            throw new IllegalArgumentException("invalid project evidence key input");
        }
        return "projects/" + projectId + "/evidence/" + artifactId + "/"
            + UUID.randomUUID() + "." + extension;
    }

    public String ideaAttachment(long projectId, String attachmentId, String extension) {
        if (attachmentId == null || !attachmentId.matches("[0-9a-f-]{36}")
                || extension == null || !extension.matches("docx|txt|md")) {
            throw new IllegalArgumentException("invalid idea attachment key input");
        }
        return "projects/" + projectId + "/idea-brief/attachments/" + attachmentId + "/"
            + UUID.randomUUID() + "." + extension;
    }

    public String marketResearchLedger(long projectId, String artifactId) {
        if (artifactId == null || !artifactId.matches("[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("invalid market ledger artifact id");
        }
        return "projects/" + projectId + "/market-research/ledgers/"
            + artifactId + "/" + UUID.randomUUID() + ".zip";
    }
}
