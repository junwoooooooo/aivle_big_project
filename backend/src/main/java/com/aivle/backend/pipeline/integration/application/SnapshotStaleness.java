package com.aivle.backend.pipeline.integration.application;

import org.springframework.stereotype.Component;

@Component
public class SnapshotStaleness {
    public boolean isStale(String resultSnapshotId, String currentSnapshotId) {
        return currentSnapshotId != null && !currentSnapshotId.equals(resultSnapshotId);
    }
}
