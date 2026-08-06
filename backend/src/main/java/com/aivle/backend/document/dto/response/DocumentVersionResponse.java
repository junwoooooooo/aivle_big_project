package com.aivle.backend.document.dto.response;

import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.document.entity.DocumentVersion;

import java.time.LocalDateTime;

public record DocumentVersionResponse(
    Long documentId,
    Long versionId,
    int versionNumber,
    String originalFileName,
    long sizeBytes,
    JobStatus parseStatus,
    LocalDateTime uploadedAt,
    JobStatus parserArtifactStatus,
    String parserVersion,
    String parserArtifactSchemaVersion,
    Integer parserBlockCount,
    LocalDateTime parsedAt
) {
    public static DocumentVersionResponse from(DocumentVersion version) {
        return new DocumentVersionResponse(
            version.getDocument().getId(),
            version.getId(),
            version.getVersionNumber(),
            version.getStoredFile().getOriginalFilename(),
            version.getStoredFile().getSizeBytes(),
            version.getParseStatus(),
            version.getUploadedAt(),
            version.getParserArtifactStatus(),
            version.getParserVersion(),
            version.getParserArtifactSchemaVersion(),
            version.getParserBlockCount(),
            version.getParsedAt()
        );
    }
}
