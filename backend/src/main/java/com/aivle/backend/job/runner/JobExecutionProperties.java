package com.aivle.backend.job.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jobs.document-processing")
public record JobExecutionProperties(
    boolean enabled,
    Duration pollInterval,
    Duration recoveryInterval,
    int batchSize,
    int maxAttempts,
    Duration executionTimeout,
    Duration retryInitialDelay,
    Duration retryMaxDelay,
    Duration staleRunningTimeout,
    String workerId
) {
    public JobExecutionProperties {
        if (pollInterval == null
            || recoveryInterval == null
            || executionTimeout == null
            || retryInitialDelay == null
            || retryMaxDelay == null
            || staleRunningTimeout == null
            || batchSize <= 0
            || maxAttempts <= 0
            || pollInterval.isNegative()
            || pollInterval.isZero()
            || recoveryInterval.isNegative()
            || recoveryInterval.isZero()
            || executionTimeout.isNegative()
            || executionTimeout.isZero()
            || retryInitialDelay.isNegative()
            || retryMaxDelay.compareTo(retryInitialDelay) < 0
            || staleRunningTimeout.compareTo(executionTimeout) <= 0) {
            throw new IllegalArgumentException("document job execution properties are invalid");
        }
    }
}
