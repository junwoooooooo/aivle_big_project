package com.aivle.backend.file.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class LocalObjectStorageAdapterTests {
    @TempDir Path root;

    @Test
    void storesReadsChecksMetadataAndDeletes() throws Exception {
        var storage = new LocalObjectStorageAdapter(properties());
        byte[] content = "{\"local\":true}".getBytes(
            StandardCharsets.UTF_8
        );

        var stored = storage.store(
            new ByteArrayInputStream(content),
            content.length,
            "application/json",
            "ai-artifacts/local.json"
        );

        assertThat(stored.checksumSha256()).hasSize(64);
        assertThat(storage.exists(stored.objectKey())).isTrue();
        assertThat(storage.open(stored.objectKey()).readAllBytes())
            .isEqualTo(content);
        assertThat(storage.metadata(stored.objectKey()).sizeBytes())
            .isEqualTo(content.length);
        storage.delete(stored.objectKey());
        assertThat(storage.exists(stored.objectKey())).isFalse();
    }

    @Test
    void rejectsTraversalAndDoesNotPretendToPresign() {
        var storage = new LocalObjectStorageAdapter(properties());

        assertThatThrownBy(() -> storage.open("../secret"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            storage.createPresignedGet("ai-artifacts/local.json")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    private ObjectStorageProperties properties() {
        return new ObjectStorageProperties(
            ObjectStorageProperties.Provider.LOCAL,
            root,
            URI.create("http://127.0.0.1:9000"),
            URI.create("http://127.0.0.1:9000"),
            "us-east-1",
            "unused",
            "",
            "",
            true,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofMinutes(1),
            Duration.ofMinutes(1),
            DataSize.ofMegabytes(1),
            List.of("application/json")
        );
    }
}
