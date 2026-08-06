package com.aivle.backend.marketing.generation;

import com.aivle.backend.job.runner.JobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(
    prefix = "app.jobs.document-processing",
    name = "enabled",
    havingValue = "true"
)
@RequiredArgsConstructor
public class MarketingGenerationWakeListener {
    private final JobRunner jobRunner;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MarketingGenerationRequested event) {
        jobRunner.wake(event.jobId());
    }
}
