package com.aivle.backend.job.runner;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.jobs.document-processing",
    name = "enabled",
    havingValue = "true"
)
public class JobRecoveryScheduler {
    private final JobRecoveryService recoveryService;

    @Scheduled(fixedDelayString = "${app.jobs.document-processing.recovery-interval}")
    public void recover() {
        recoveryService.recoverStaleJobs();
    }
}
