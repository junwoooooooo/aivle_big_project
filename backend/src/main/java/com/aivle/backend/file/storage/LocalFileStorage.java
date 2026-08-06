package com.aivle.backend.file.storage;

import com.aivle.backend.file.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {
    private final FileStorageProperties properties;

    @Override
    public StoredFileResult store(InputStream inputStream, long sizeBytes, String safeExtension, String storageKey) throws IOException {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        MessageDigest digest = sha256();
        long actualSize = 0;
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW);
             OutputStream digestOutput = new java.security.DigestOutputStream(output, digest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digestOutput.write(buffer, 0, read);
                actualSize += read;
            }
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
        if (actualSize != sizeBytes) {
            Files.deleteIfExists(target);
            throw new IOException("stored byte count does not match expected size");
        }
        return new StoredFileResult(storageKey, target.getFileName().toString(), actualSize,
                HexFormat.of().formatHex(digest.digest()));
    }

    @Override public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(resolve(storageKey), StandardOpenOption.READ);
    }

    @Override public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    @Override public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        Path root = properties.root().toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("storage key escapes configured root");
        return target;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
