package com.aivle.backend.aitask.application;

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
public class SystemSmokeTaskWakeListener {

    private final JobRunner jobRunner;

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    public void on(SystemSmokeTaskRequested event) {
        jobRunner.wake(event.jobId());
    }
}
