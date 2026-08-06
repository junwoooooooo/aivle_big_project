package com.aivle.backend.file.reconciliation;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public interface StorageInventory {
    List<StorageObject> listActive() throws IOException;

    List<StorageObject> listQuarantined() throws IOException;

    void quarantine(String storageKey) throws IOException;

    void deleteQuarantined(String storageKey) throws IOException;

    record StorageObject(String storageKey, Instant lastModifiedAt) {
    }
}
