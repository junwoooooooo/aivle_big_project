package com.aivle.backend.aitask.application;

import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.object.ObjectStorageProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArtifactIntegrityService {
    private final ObjectStorageProperties properties;

    public VerifiedArtifact verify(
        ObjectStoragePort objectStorage,
        String objectKey,
        String expectedContentType,
        long reportedSize,
        String reportedChecksum
    ) throws IOException {
        return verify(
            objectStorage,
            objectKey,
            expectedContentType,
            reportedSize,
            reportedChecksum,
            properties.maxArtifactBytes()
        );
    }

    public VerifiedArtifact verify(
        ObjectStoragePort objectStorage,
        String objectKey,
        String expectedContentType,
        long reportedSize,
        String reportedChecksum,
        long maxBytes
    ) throws IOException {
        if (
            !properties.allowedContentTypes()
                .contains(expectedContentType)
        ) {
            throw new IOException("artifact content type is not allowed");
        }
        var metadata = objectStorage.metadata(objectKey);
        if (
            metadata.sizeBytes() <= 0
            || metadata.sizeBytes() > maxBytes
            || metadata.sizeBytes() != reportedSize
        ) {
            throw new IOException("artifact size mismatch");
        }
        if (!sameContentType(
            expectedContentType,
            metadata.contentType()
        )) {
            throw new IOException("artifact content type mismatch");
        }
        byte[] content;
        try (InputStream input = objectStorage.open(objectKey)) {
            content = readLimited(
                input,
                maxBytes
            );
        }
        if (content.length != metadata.sizeBytes()) {
            throw new IOException("artifact byte count mismatch");
        }
        String checksum = HexFormat.of().formatHex(
            sha256().digest(content)
        );
        if (
            reportedChecksum == null
            || !reportedChecksum.equals(
                "sha256:" + checksum
            )
        ) {
            throw new IOException("artifact checksum mismatch");
        }
        return new VerifiedArtifact(
            content,
            metadata.contentType(),
            checksum
        );
    }

    private byte[] readLimited(InputStream input, long max)
        throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > max) {
                throw new IOException("artifact exceeds size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean sameContentType(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return expected.equalsIgnoreCase(
            actual.split(";", 2)[0].trim()
        );
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
    }

    public record VerifiedArtifact(
        byte[] content,
        String contentType,
        String checksumSha256
    ) {
    }
}
