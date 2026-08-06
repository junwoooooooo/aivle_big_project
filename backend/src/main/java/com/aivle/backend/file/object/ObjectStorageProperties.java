package com.aivle.backend.file.object;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.object-storage")
public record ObjectStorageProperties(
    Provider provider,
    Path localRoot,
    URI endpoint,
    URI publicEndpoint,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    boolean pathStyleAccess,
    Duration connectTimeout,
    Duration readTimeout,
    Duration presignedGetExpiry,
    Duration presignedPutExpiry,
    DataSize artifactMaxSize,
    List<String> allowedContentTypes
) {
    public enum Provider {
        LOCAL,
        S3
    }

    public ObjectStorageProperties {
        provider = provider == null ? Provider.LOCAL : provider;
        allowedContentTypes = allowedContentTypes == null
            ? List.of()
            : List.copyOf(allowedContentTypes);
    }

    public long maxArtifactBytes() {
        return artifactMaxSize.toBytes();
    }
}
