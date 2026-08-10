package com.aivle.backend.pipeline.conceptportfolio.worker;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.task-run.concept-portfolio")
public record ConceptPortfolioExecutionProperties(
    Duration lease,
    Duration heartbeatInterval,
    Duration taskTimeout,
    Duration aiDeadline,
    Integer executorThreads,
    Integer queueCapacity
) {
    public ConceptPortfolioExecutionProperties {
        lease = lease == null ? Duration.ofSeconds(90) : lease;
        heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(20) : heartbeatInterval;
        taskTimeout = taskTimeout == null ? Duration.ofMinutes(20) : taskTimeout;
        aiDeadline = aiDeadline == null ? Duration.ofMinutes(14) : aiDeadline;
        executorThreads = executorThreads == null ? 2 : executorThreads;
        queueCapacity = queueCapacity == null ? 4 : queueCapacity;
        if (lease.isZero() || lease.isNegative() || heartbeatInterval.isZero()
                || heartbeatInterval.isNegative() || taskTimeout.isZero() || taskTimeout.isNegative()
                || aiDeadline.isZero() || aiDeadline.isNegative()
                || heartbeatInterval.compareTo(lease) >= 0 || aiDeadline.compareTo(taskTimeout) >= 0
                || executorThreads < 1 || executorThreads > 8 || queueCapacity < 1 || queueCapacity > 32) {
            throw new IllegalArgumentException("Concept Portfolio execution timing is invalid");
        }
    }
}
