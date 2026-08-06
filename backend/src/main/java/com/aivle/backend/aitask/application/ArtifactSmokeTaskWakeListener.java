package com.aivle.backend.aitask.application;

import com.aivle.backend.job.runner.JobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
public class ArtifactSmokeTaskWakeListener {
    private final JobRunner jobRunner;

    @Value("${app.e2e.defer-artifact-wake:false}")
    private boolean deferArtifactWake;

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    public void on(ArtifactSmokeTaskRequested event) {
        if (deferArtifactWake) {
            return;
        }
        jobRunner.wake(event.jobId());
    }
}
