package com.aivle.backend.file.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class S3ObjectStorageEndpointContractTests {
    @Test
    void keepsInternalS3EndpointSeparateFromBrowserPresignedEndpoint() {
        ObjectStorageProperties properties = new ObjectStorageProperties(
            ObjectStorageProperties.Provider.S3,
            Path.of("./build/object-storage-unused"),
            URI.create("http://minio:9000"),
            URI.create("http://localhost:9000"),
            "us-east-1",
            "aivle-ai-artifacts",
            "test-access-key",
            "test-secret-key",
            true,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(5),
            DataSize.ofMegabytes(20),
            List.of("image/jpeg", "image/png", "image/webp")
        );
        S3ObjectStorageConfiguration configuration = new S3ObjectStorageConfiguration();
        var client = configuration.objectStorageS3Client(properties);
        var presigner = configuration.objectStorageS3Presigner(properties);
        try {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                .contains(URI.create("http://minio:9000"));

            S3ObjectStorageAdapter storage = new S3ObjectStorageAdapter(
                properties, client, presigner);
            URI signed = storage.createPresignedGet(
                "ai-artifacts/00000000-0000-4000-8000-000000000001.jpg");

            assertThat(signed.getScheme()).isEqualTo("http");
            assertThat(signed.getHost()).isEqualTo("localhost");
            assertThat(signed.getPort()).isEqualTo(9000);
            assertThat(signed.getPath()).isEqualTo(
                "/aivle-ai-artifacts/ai-artifacts/00000000-0000-4000-8000-000000000001.jpg");
            assertThat(signed.getRawQuery()).contains("X-Amz-Algorithm=");
            assertThat(signed.getRawQuery()).contains("X-Amz-Signature=");
        } finally {
            presigner.close();
            client.close();
        }
    }

    @Test
    void rejectsDockerOnlyServiceHostnameAsPublicBrowserEndpoint() {
        assertThatThrownBy(() -> properties(URI.create("http://minio:9000")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("browser reachable");
    }

    private ObjectStorageProperties properties(URI publicEndpoint) {
        return new ObjectStorageProperties(
            ObjectStorageProperties.Provider.S3,
            Path.of("./build/object-storage-unused"),
            URI.create("http://minio:9000"),
            publicEndpoint,
            "us-east-1",
            "aivle-ai-artifacts",
            "test-access-key",
            "test-secret-key",
            true,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(5),
            DataSize.ofMegabytes(20),
            List.of("image/jpeg", "image/png", "image/webp")
        );
    }
}
