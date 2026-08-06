package com.aivle.backend.job.runner;

import com.aivle.backend.document.application.DocumentProcessingRequested;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(
    prefix = "app.jobs.document-processing",
    name = "enabled",
    havingValue = "true"
)
public class DocumentProcessingWakeListener {
    private final JobRunner jobRunner;
    private final TaskExecutor wakeExecutor;

    public DocumentProcessingWakeListener(
        JobRunner jobRunner,
        @Qualifier("documentJobWakeExecutor") TaskExecutor wakeExecutor
    ) {
        this.jobRunner = jobRunner;
        this.wakeExecutor = wakeExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentProcessingRequested(DocumentProcessingRequested event) {
        wakeExecutor.execute(() -> jobRunner.wake(event.jobId()));
    }
}
