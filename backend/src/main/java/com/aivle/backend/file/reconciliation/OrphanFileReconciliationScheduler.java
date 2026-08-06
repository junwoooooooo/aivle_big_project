package com.aivle.backend.file.reconciliation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.storage.reconciliation",
    name = "enabled",
    havingValue = "true"
)
public class OrphanFileReconciliationScheduler {
    private final OrphanFileReconciliationService reconciliationService;

    @Scheduled(cron = "${app.storage.reconciliation.schedule}")
    public void reconcile() {
        try {
            var result = reconciliationService.reconcile();
            log.info(
                "storage reconciliation completed: dryRun={}, candidates={}, quarantined={}, deleted={}",
                result.dryRun(),
                result.candidateCount(),
                result.quarantinedCount(),
                result.deletedCount()
            );
        } catch (Exception exception) {
            log.error(
                "storage reconciliation failed without applying further mutations",
                exception
            );
        }
    }
}
