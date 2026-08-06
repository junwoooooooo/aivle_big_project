package com.aivle.backend.file.reconciliation;

import com.aivle.backend.file.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrphanFileReconciliationService {
    private final StoredFileRepository storedFileRepository;
    private final StorageInventory storageInventory;
    private final ReconciliationProperties properties;
    private final Clock jobClock;

    public ReconciliationResult reconcile() throws IOException {
        Instant now = jobClock.instant();

        // Resolve the database source of truth before any storage mutation.
        Set<String> referencedKeys =
            new HashSet<>(storedFileRepository.findAllReferencedStorageKeys());
        List<OrphanCandidate> candidates = storageInventory.listActive().stream()
            .filter(object -> !referencedKeys.contains(object.storageKey()))
            .filter(object -> object.lastModifiedAt().isBefore(
                now.minus(properties.minimumAge())
            ))
            .limit(properties.batchSize())
            .map(object -> new OrphanCandidate(
                object.storageKey(),
                object.lastModifiedAt()
            ))
            .toList();

        int quarantined = 0;
        int deleted = 0;
        if (!properties.dryRun()) {
            for (OrphanCandidate candidate : candidates) {
                storageInventory.quarantine(candidate.storageKey());
                quarantined++;
            }
            for (StorageInventory.StorageObject object :
                storageInventory.listQuarantined()) {
                if (deleted >= properties.batchSize()) {
                    break;
                }
                if (object.lastModifiedAt().isBefore(
                    now.minus(properties.quarantineRetention())
                )) {
                    storageInventory.deleteQuarantined(object.storageKey());
                    deleted++;
                }
            }
        }

        return new ReconciliationResult(
            properties.dryRun(),
            candidates.size(),
            quarantined,
            deleted
        );
    }

    public record ReconciliationResult(
        boolean dryRun,
        int candidateCount,
        int quarantinedCount,
        int deletedCount
    ) {
    }
}
