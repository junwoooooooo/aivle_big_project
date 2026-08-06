package com.aivle.backend.file.reconciliation;

import java.time.Instant;

public record OrphanCandidate(String storageKey, Instant lastModifiedAt) {
}
