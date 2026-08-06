package com.aivle.backend.file.object;

import com.aivle.backend.common.entity.StorageType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.object-storage.provider",
    havingValue = "local",
    matchIfMissing = true
)
public class LocalObjectStorageAdapter implements ObjectStoragePort {
    private final ObjectStorageProperties properties;

    @Override
    public StoredObject store(
        InputStream input,
        long expectedSize,
        String contentType,
        String objectKey
    ) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        MessageDigest digest = sha256();
        long actualSize = 0;
        try (
            OutputStream fileOutput = Files.newOutputStream(
                target,
                StandardOpenOption.CREATE_NEW
            );
            OutputStream output = new DigestOutputStream(
                fileOutput,
                digest
            )
        ) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                actualSize += read;
            }
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
        if (actualSize != expectedSize) {
            Files.deleteIfExists(target);
            throw new IOException(
                "stored byte count does not match expected size"
            );
        }
        return new StoredObject(
            objectKey,
            actualSize,
            contentType,
            HexFormat.of().formatHex(digest.digest())
        );
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        return Files.newInputStream(resolve(objectKey));
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolve(objectKey));
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.isRegularFile(resolve(objectKey));
    }

    @Override
    public ObjectMetadata metadata(String objectKey)
        throws IOException {
        Path target = resolve(objectKey);
        return new ObjectMetadata(
            objectKey,
            Files.size(target),
            Files.probeContentType(target)
        );
    }

    @Override
    public URI createPresignedGet(String objectKey) {
        throw new UnsupportedOperationException(
            "local object storage does not issue presigned URLs"
        );
    }

    @Override
    public URI createPresignedPut(
        String objectKey,
        String contentType
    ) {
        throw new UnsupportedOperationException(
            "local object storage does not issue presigned URLs"
        );
    }

    @Override
    public StorageType storageType() {
        return StorageType.LOCAL;
    }

    private Path resolve(String objectKey) {
        Path root = properties.localRoot()
            .toAbsolutePath()
            .normalize();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException(
                "object key escapes configured root"
            );
        }
        return target;
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
}
