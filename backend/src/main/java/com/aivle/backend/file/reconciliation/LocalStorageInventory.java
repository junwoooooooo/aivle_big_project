package com.aivle.backend.file.reconciliation;

import com.aivle.backend.file.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class LocalStorageInventory implements StorageInventory {
    private static final String QUARANTINE_DIRECTORY = ".quarantine";

    private final FileStorageProperties properties;

    @Override
    public List<StorageObject> listActive() throws IOException {
        Path root = root();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> !path.startsWith(quarantineRoot()))
                .map(path -> object(root, path))
                .toList();
        }
    }

    @Override
    public List<StorageObject> listQuarantined() throws IOException {
        Path quarantine = quarantineRoot();
        if (!Files.isDirectory(quarantine)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(quarantine)) {
            return paths
                .filter(Files::isRegularFile)
                .map(path -> object(quarantine, path))
                .toList();
        }
    }

    @Override
    public void quarantine(String storageKey) throws IOException {
        Path source = resolve(root(), storageKey);
        Path target = resolve(quarantineRoot(), storageKey);
        if (!Files.isRegularFile(source)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void deleteQuarantined(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(quarantineRoot(), storageKey));
    }

    private StorageObject object(Path base, Path path) {
        try {
            String key = base.relativize(path).toString().replace('\\', '/');
            Instant modified = Files.getLastModifiedTime(path).toInstant();
            return new StorageObject(key, modified);
        } catch (IOException exception) {
            throw new StorageInventoryReadException(exception);
        }
    }

    private Path root() {
        return properties.root().toAbsolutePath().normalize();
    }

    private Path quarantineRoot() {
        return root().resolve(QUARANTINE_DIRECTORY).normalize();
    }

    private Path resolve(Path base, String storageKey) {
        Path target = base.resolve(storageKey).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("storage key escapes configured root");
        }
        return target;
    }

    private static final class StorageInventoryReadException extends RuntimeException {
        private StorageInventoryReadException(IOException cause) {
            super(cause);
        }
    }
}
