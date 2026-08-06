package com.aivle.backend.journey.boundary;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegulatoryBoundaryRunStateService {
    private final RegulatoryBoundaryRunRepository runs;
    public RegulatoryBoundaryRunStateService(RegulatoryBoundaryRunRepository runs) { this.runs = runs; }

    @Transactional
    public RegulatoryBoundaryRun start(String taskRunId) {
        RegulatoryBoundaryRun run = byTask(taskRunId); run.start(); return run;
    }
    @Transactional
    public RegulatoryBoundaryRun advance(String taskRunId, RegulatoryBoundaryRun.State state) {
        RegulatoryBoundaryRun run = byTask(taskRunId); run.advance(state); return run;
    }
    @Transactional
    public RegulatoryBoundaryRun requeue(String taskRunId) {
        RegulatoryBoundaryRun run = byTask(taskRunId); run.retryQueued(); return run;
    }
    @Transactional
    public RegulatoryBoundaryRun fail(String taskRunId, String code) {
        RegulatoryBoundaryRun run = byTask(taskRunId); run.fail(code, LocalDateTime.now()); return run;
    }
    private RegulatoryBoundaryRun byTask(String taskRunId) {
        return runs.findByTaskRunIdAndDeletedAtIsNull(taskRunId).orElseThrow();
    }
}
