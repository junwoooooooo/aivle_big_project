package com.aivle.backend.pipeline.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.integration.application.SnapshotStaleness;
import org.junit.jupiter.api.Test;

class SnapshotStalenessTests {
    private final SnapshotStaleness policy = new SnapshotStaleness();
    @Test void marksPastSnapshotResultStale() {
        assertThat(policy.isStale("snapshot-old", "snapshot-current")).isTrue();
    }
    @Test void keepsCurrentSnapshotResultUsable() {
        assertThat(policy.isStale("snapshot-current", "snapshot-current")).isFalse();
    }
}
