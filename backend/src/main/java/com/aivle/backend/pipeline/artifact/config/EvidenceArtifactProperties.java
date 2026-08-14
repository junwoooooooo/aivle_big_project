package com.aivle.backend.pipeline.artifact.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.evidence-artifact")
public record EvidenceArtifactProperties(DataSize maxSize, List<String> allowedExtensions) {
    public EvidenceArtifactProperties {
        maxSize = maxSize == null ? DataSize.ofMegabytes(20) : maxSize;
        allowedExtensions = allowedExtensions == null
            ? List.of("pdf", "csv", "xlsx", "xls", "docx", "txt", "png", "jpg", "jpeg", "webp")
            : allowedExtensions.stream().map(String::toLowerCase).toList();
    }

    public long maxSizeBytes() { return maxSize.toBytes(); }
}
