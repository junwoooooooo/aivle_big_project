package com.aivle.backend.file.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Tag("minio")
class S3ObjectStorageMinioTests {
    private static S3Client client;
    private static S3Presigner presigner;
    private static S3ObjectStorageAdapter storage;
    private static String bucket;

    @BeforeAll
    static void start() {
        String endpoint = required("MINIO_TEST_ENDPOINT");
        bucket = "aivle-test-" + UUID.randomUUID();
        ObjectStorageProperties properties =
            new ObjectStorageProperties(
                ObjectStorageProperties.Provider.S3,
                Path.of("./build/minio-unused"),
                URI.create(endpoint),
                URI.create(endpoint),
                "us-east-1",
                bucket,
                required("MINIO_TEST_ACCESS_KEY"),
                required("MINIO_TEST_SECRET_KEY"),
                true,
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                Duration.ofMinutes(1),
                Duration.ofMinutes(1),
                DataSize.ofMegabytes(1),
                List.of("application/json")
            );
        S3ObjectStorageConfiguration configuration =
            new S3ObjectStorageConfiguration();
        client = configuration.objectStorageS3Client(properties);
        presigner =
            configuration.objectStorageS3Presigner(properties);
        client.createBucket(builder -> builder.bucket(bucket));
        storage = new S3ObjectStorageAdapter(
            properties,
            client,
            presigner
        );
    }

    @AfterAll
    static void stop() {
        if (client != null && bucket != null) {
            client.listObjectsV2(builder -> builder.bucket(bucket))
                .contents()
                .forEach(object ->
                    client.deleteObject(builder -> builder
                        .bucket(bucket)
                        .key(object.key())
                    )
                );
            client.deleteBucket(builder -> builder.bucket(bucket));
            client.close();
        }
        if (presigner != null) {
            presigner.close();
        }
    }

    @Test
    void storesReadsPresignsAndDeletesWithoutOverwrite()
        throws Exception {
        byte[] source = "{\"source\":true}".getBytes(
            StandardCharsets.UTF_8
        );
        String sourceKey =
            "ai-artifacts/" + UUID.randomUUID() + ".json";
        var stored = storage.store(
            new ByteArrayInputStream(source),
            source.length,
            "application/json",
            sourceKey
        );

        assertThat(stored.checksumSha256()).hasSize(64);
        assertThat(storage.exists(sourceKey)).isTrue();
        assertThat(storage.open(sourceKey).readAllBytes())
            .isEqualTo(source);
        assertThat(storage.metadata(sourceKey).contentType())
            .isEqualTo("application/json");
        assertThatThrownBy(() ->
            storage.createPresignedGet("../private")
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store(
            new ByteArrayInputStream(source),
            source.length,
            "application/json",
            sourceKey
        )).isInstanceOf(java.io.IOException.class);

        HttpClient http = HttpClient.newHttpClient();
        var getResponse = http.send(
            HttpRequest.newBuilder(
                storage.createPresignedGet(sourceKey)
            ).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );
        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).isEqualTo(source);

        byte[] output = "{\"result\":true}".getBytes(
            StandardCharsets.UTF_8
        );
        String outputKey =
            "ai-artifacts/" + UUID.randomUUID() + ".json";
        var putResponse = http.send(
            HttpRequest.newBuilder(
                storage.createPresignedPut(
                    outputKey,
                    "application/json"
                )
            )
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(output))
                .build(),
            HttpResponse.BodyHandlers.discarding()
        );
        assertThat(putResponse.statusCode()).isBetween(200, 299);
        assertThat(storage.open(outputKey).readAllBytes())
            .isEqualTo(output);

        storage.delete(sourceKey);
        storage.delete(outputKey);
        assertThat(storage.exists(sourceKey)).isFalse();
    }

    @Test
    void storesDocumentSourceAndParserArtifactKeys()
        throws Exception {
        byte[] docx = {0x50, 0x4b, 0x03, 0x04};
        String sourceKey =
            "projects/1/documents/2/versions/3/source/"
                + UUID.randomUUID() + ".docx";
        storage.store(
            new ByteArrayInputStream(docx),
            docx.length,
            "application/vnd.openxmlformats-officedocument"
                + ".wordprocessingml.document",
            sourceKey
        );

        byte[] artifact = "{\"blocks\":[]}".getBytes(
            StandardCharsets.UTF_8
        );
        String checksum = java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(artifact)
        );
        String artifactKey =
            "projects/1/documents/2/versions/3/parser/"
                + "spring-docx-blocks-v2/" + checksum + ".json";
        storage.store(
            new ByteArrayInputStream(artifact),
            artifact.length,
            "application/json",
            artifactKey
        );

        assertThat(storage.open(sourceKey).readAllBytes())
            .isEqualTo(docx);
        assertThat(storage.open(artifactKey).readAllBytes())
            .isEqualTo(artifact);
        storage.delete(sourceKey);
        storage.delete(artifactKey);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                name + " is required for minioTest"
            );
        }
        return value;
    }
}
