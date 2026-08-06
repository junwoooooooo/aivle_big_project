package com.aivle.backend.file.storage;

import com.aivle.backend.file.config.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LocalFileStorageTests {
    @TempDir Path root;

    @Test
    void storesActualSizeAndChecksumWithoutClosingCallerStream() throws Exception {
        byte[] content = {1, 2, 3, 4};
        TrackingInputStream input = new TrackingInputStream(content);
        LocalFileStorage storage = storage();

        FileStorage.StoredFileResult result = storage.store(
            input,
            content.length,
            "docx",
            "documents/one.docx"
        );

        assertThat(input.closed).isFalse();
        assertThat(result.sizeBytes()).isEqualTo(content.length);
        assertThat(result.checksumSha256()).isEqualTo(
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))
        );
        assertThat(storage.exists("documents/one.docx")).isTrue();
    }

    @Test
    void removesPartialFileWhenExpectedSizeDoesNotMatch() {
        LocalFileStorage storage = storage();

        assertThatThrownBy(() -> storage.store(
            new ByteArrayInputStream(new byte[] {1, 2, 3}),
            99,
            "docx",
            "documents/mismatch.docx"
        )).isInstanceOf(IOException.class);

        assertThat(storage.exists("documents/mismatch.docx")).isFalse();
    }

    @Test
    void rejectsStorageKeyEscapingRoot() {
        assertThatThrownBy(() -> storage().store(
            new ByteArrayInputStream(new byte[] {1}),
            1,
            "docx",
            "../outside.docx"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openReturnsCallerOwnedReadableStream() throws Exception {
        LocalFileStorage storage = storage();
        storage.store(
            new ByteArrayInputStream(new byte[] {7, 8}),
            2,
            "docx",
            "documents/read.docx"
        );

        try (InputStream input = storage.open("documents/read.docx")) {
            assertThat(input.readAllBytes()).containsExactly(7, 8);
        }
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        LocalFileStorage storage = storage();
        storage.delete("documents/missing.docx");
        assertThat(Files.exists(root.resolve("documents/missing.docx"))).isFalse();
    }

    private LocalFileStorage storage() {
        return new LocalFileStorage(new FileStorageProperties(
            root,
            DataSize.ofMegabytes(20),
            DataSize.ofMegabytes(10),
            List.of("docx"),
            List.of("png")
        ));
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
