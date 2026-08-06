package com.aivle.backend.job.runner;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JobWorkerIdentity {
    private final String value;

    public JobWorkerIdentity(JobExecutionProperties properties) {
        String configured = properties.workerId();
        this.value = configured == null || configured.isBlank()
            ? "worker-" + UUID.randomUUID()
            : configured.trim();
    }

    public String value() {
        return value;
    }
}
