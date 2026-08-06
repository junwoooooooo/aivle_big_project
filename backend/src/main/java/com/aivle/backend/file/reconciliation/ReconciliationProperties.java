package com.aivle.backend.file.reconciliation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.storage.reconciliation")
public record ReconciliationProperties(
    boolean enabled,
    boolean dryRun,
    Duration minimumAge,
    Duration quarantineRetention,
    int batchSize,
    String schedule
) {
    public ReconciliationProperties {
        if (minimumAge == null
            || minimumAge.isNegative()
            || minimumAge.isZero()
            || quarantineRetention == null
            || quarantineRetention.isNegative()
            || quarantineRetention.isZero()
            || batchSize <= 0
            || schedule == null
            || schedule.isBlank()) {
            throw new IllegalArgumentException("storage reconciliation properties are invalid");
        }
    }
}
