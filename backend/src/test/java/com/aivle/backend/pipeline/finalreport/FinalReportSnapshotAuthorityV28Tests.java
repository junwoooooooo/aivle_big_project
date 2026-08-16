package com.aivle.backend.pipeline.finalreport;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.finalreport.domain.FinalReportSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FinalReportSnapshotAuthorityV28Tests {
    private static final String HASH_A = "sha256:" + "a".repeat(64);
    private static final String HASH_B = "sha256:" + "b".repeat(64);

    @Test
    void currentCapableSnapshotRequiresExactLineageManifestV2AndCommandIdentity() {
        FinalReportSnapshot snapshot = FinalReportSnapshot.create(41L, 1, "{}", HASH_A, "{}",
            Instant.parse("2026-08-17T00:00:00Z"), 7L, "seed-1", 11L, 4, 3,
            HASH_B, "command-1", HASH_A, 2);

        assertThat(snapshot.hasExactLineage()).isTrue();
        assertThat(snapshot.getSourceMarketSeedSnapshotId()).isEqualTo("seed-1");
        assertThat(snapshot.getSourceSelectionId()).isEqualTo(11L);
        assertThat(snapshot.getSourceSelectionRevision()).isEqualTo(4);
        assertThat(snapshot.getSourceBmPlanRevision()).isEqualTo(3);
    }

    @Test
    void legacySnapshotWithoutLineageCanNeverBeCurrent() {
        FinalReportSnapshot legacy = FinalReportSnapshot.create(41L, 1, "{}", HASH_A, "{}",
            Instant.parse("2026-08-17T00:00:00Z"), 7L);

        assertThat(legacy.hasExactLineage()).isFalse();
    }

    @Test
    void newCommandKeyCanCreateAnotherImmutableVersionForSameManifest() {
        FinalReportSnapshot first = FinalReportSnapshot.create(41L, 1, "{}", HASH_A, "{\"version\":1}",
            Instant.parse("2026-08-17T00:00:00Z"), 7L, "seed-1", 11L, 4, 3,
            HASH_B, "command-1", HASH_A, 2);
        FinalReportSnapshot second = FinalReportSnapshot.create(41L, 2, "{}", HASH_A, "{\"version\":2}",
            Instant.parse("2026-08-17T00:01:00Z"), 7L, "seed-1", 11L, 4, 3,
            HASH_B, "command-2", HASH_A, 2);

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getReportVersion()).isEqualTo(2);
        assertThat(first.getReportJson()).contains("version\":1");
    }
}
