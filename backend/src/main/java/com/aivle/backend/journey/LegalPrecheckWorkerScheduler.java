package com.aivle.backend.journey;

import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.TaskRunWorker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LegalPrecheckWorkerScheduler {
    private final TaskRunWorker worker;
    public LegalPrecheckWorkerScheduler(TaskRunWorker worker) { this.worker = worker; }

    @Scheduled(fixedDelayString = "${app.task-run.legal-precheck-poll-interval-ms:1000}")
    public void poll() { worker.executeOne(TaskType.IDEA_LEGAL_PRECHECK, "legal-precheck-worker"); }
}
