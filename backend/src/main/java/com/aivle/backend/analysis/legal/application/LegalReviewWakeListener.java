package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.job.runner.JobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "app.jobs.document-processing", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LegalReviewWakeListener {
    private final JobRunner jobRunner;
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(LegalReviewRequested event) {
        jobRunner.wake(event.jobId());
    }
}
