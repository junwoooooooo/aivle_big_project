package com.aivle.backend.file.storage;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorage {
    StoredFileResult store(InputStream inputStream, long sizeBytes, String safeExtension, String storageKey) throws IOException;
    InputStream open(String storageKey) throws IOException;
    void delete(String storageKey) throws IOException;
    boolean exists(String storageKey);

    record StoredFileResult(String storageKey, String storedFilename, long sizeBytes, String checksumSha256) {}
}
