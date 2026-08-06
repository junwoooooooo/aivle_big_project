package com.aivle.backend.analysis.feasibility.application;

import com.aivle.backend.job.runner.JobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@ConditionalOnProperty(prefix = "app.jobs.document-processing", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FeasibilityWakeListener {
    private final JobRunner jobRunner;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FeasibilityRequested event) {
        jobRunner.wake(event.jobId());
    }
}
