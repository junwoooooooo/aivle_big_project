package com.aivle.backend.file.reconciliation;

import com.aivle.backend.file.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrphanFileReconciliationServiceTests {
    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

    @Test
    void dryRunReportsOnlyOldUnreferencedFilesWithoutMutatingStorage()
        throws IOException {
        StoredFileRepository repository = mock(StoredFileRepository.class);
        when(repository.findAllReferencedStorageKeys()).thenReturn(List.of("referenced"));
        FakeInventory inventory = new FakeInventory(
            List.of(
                object("referenced", Duration.ofHours(2)),
                object("young-upload", Duration.ofMinutes(10)),
                object("old-orphan", Duration.ofHours(2))
            ),
            List.of()
        );

        var result = service(repository, inventory, true).reconcile();

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.quarantinedCount()).isZero();
        assertThat(result.deletedCount()).isZero();
        assertThat(inventory.quarantined).isEmpty();
        assertThat(inventory.deleted).isEmpty();
    }

    @Test
    void activeRunQuarantinesOldOrphanAndDeletesExpiredQuarantine()
        throws IOException {
        StoredFileRepository repository = mock(StoredFileRepository.class);
        when(repository.findAllReferencedStorageKeys()).thenReturn(List.of());
        FakeInventory inventory = new FakeInventory(
            List.of(object("old-orphan", Duration.ofHours(2))),
            List.of(
                object("expired", Duration.ofDays(8)),
                object("retained", Duration.ofDays(1))
            )
        );

        var result = service(repository, inventory, false).reconcile();

        assertThat(result.quarantinedCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(inventory.quarantined).containsExactly("old-orphan");
        assertThat(inventory.deleted).containsExactly("expired");
    }

    @Test
    void databaseFailureStopsBeforeStorageInspectionOrMutation() {
        StoredFileRepository repository = mock(StoredFileRepository.class);
        when(repository.findAllReferencedStorageKeys())
            .thenThrow(new IllegalStateException("database unavailable"));
        StorageInventory inventory = mock(StorageInventory.class);

        assertThatThrownBy(() -> service(repository, inventory, false).reconcile())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("database unavailable");
        verifyNoInteractions(inventory);
    }

    private OrphanFileReconciliationService service(
        StoredFileRepository repository,
        StorageInventory inventory,
        boolean dryRun
    ) {
        return new OrphanFileReconciliationService(
            repository,
            inventory,
            new ReconciliationProperties(
                false,
                dryRun,
                Duration.ofHours(1),
                Duration.ofDays(7),
                100,
                "0 0 3 * * *"
            ),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static StorageInventory.StorageObject object(
        String key,
        Duration age
    ) {
        return new StorageInventory.StorageObject(key, NOW.minus(age));
    }

    private static final class FakeInventory implements StorageInventory {
        private final List<StorageObject> active;
        private final List<StorageObject> quarantine;
        private final List<String> quarantined = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();

        private FakeInventory(
            List<StorageObject> active,
            List<StorageObject> quarantine
        ) {
            this.active = active;
            this.quarantine = quarantine;
        }

        @Override
        public List<StorageObject> listActive() {
            return active;
        }

        @Override
        public List<StorageObject> listQuarantined() {
            return quarantine;
        }

        @Override
        public void quarantine(String storageKey) {
            quarantined.add(storageKey);
        }

        @Override
        public void deleteQuarantined(String storageKey) {
            deleted.add(storageKey);
        }
    }
}
