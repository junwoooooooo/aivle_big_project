package com.aivle.backend.file.object;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
        if (provider == Provider.S3) {
            requireHttpEndpoint(endpoint, "endpoint");
            requireHttpEndpoint(publicEndpoint, "public-endpoint");
            String publicHost = publicEndpoint.getHost().toLowerCase(Locale.ROOT);
            if (List.of("minio", "backend", "ai-server").contains(publicHost)) {
                throw new IllegalArgumentException(
                    "app.object-storage.public-endpoint must be browser reachable"
                );
            }
        }
    }

    public long maxArtifactBytes() {
        return artifactMaxSize.toBytes();
    }

    private static void requireHttpEndpoint(URI value, String name) {
        if (value == null || value.getHost() == null
            || !("http".equalsIgnoreCase(value.getScheme())
                || "https".equalsIgnoreCase(value.getScheme()))) {
            throw new IllegalArgumentException(
                "app.object-storage." + name + " must be an absolute HTTP(S) URI"
            );
        }
    }
}
