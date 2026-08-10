package com.aivle.backend.jobevent;

import com.aivle.backend.taskrun.domain.TaskAttempt;
import com.aivle.backend.taskrun.domain.TaskAttemptState;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskAttemptRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiTaskProgressService {
    public enum Outcome { ACCEPTED, IGNORED, NOT_FOUND, INVALID }

    private final TaskRunRepository runs;
    private final TaskAttemptRepository attempts;
    private final JobEventPublisher events;

    public AiTaskProgressService(TaskRunRepository runs, TaskAttemptRepository attempts,
            JobEventPublisher events) {
        this.runs = runs; this.attempts = attempts; this.events = events;
    }

    @Transactional
    public Outcome accept(AiTaskProgressController.ProgressRequest request) {
        TaskRun run = runs.findById(request.taskRunId()).orElse(null);
        if (run == null) return Outcome.NOT_FOUND;
        if (run.getTaskType() != TaskType.CONCEPT_PORTFOLIO_V2_RUN
                || !run.getCorrelationId().equals(request.correlationId())) return Outcome.INVALID;
        if (run.getState() != TaskRunState.RUNNING || run.terminal()
                || !request.taskAttemptId().equals(run.getCurrentAttemptId())) return Outcome.IGNORED;
        TaskAttempt attempt = attempts.findByIdAndTaskRunId(
            request.taskAttemptId(), request.taskRunId()).orElse(null);
        if (attempt == null || attempt.getState() != TaskAttemptState.RUNNING) return Outcome.IGNORED;
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("traceSequence", request.sequence());
            params.put("traceStatus", request.status());
            if (request.reasonCode() != null) params.put("reasonCode", request.reasonCode());
            if (request.decision() != null) params.put("decision", request.decision());
            String key = messageKey(request.stage(), request.action(), request.reasonCode());
            events.publish(new JobEventPublisher.Command(
                run.getProject().getId(), run.getId(), run.getId(),
                "TRACE_" + request.stage(), "job.concept-portfolio.trace",
                JobEvent.Status.RUNNING, key, params, null));
            return Outcome.ACCEPTED;
        } catch (IllegalStateException lateTerminal) {
            return Outcome.IGNORED;
        }
    }

    static String messageKey(String stage, String action, String reasonCode) {
        if ("REJECTED".equals(action)) {
            if ("DUPLICATE".equals(reasonCode)) return "job.concept-portfolio.trace.excluded-duplicate";
            if ("OUT_OF_SCOPE".equals(reasonCode)) return "job.concept-portfolio.trace.excluded-scope";
            if ("LEGAL_NOT_IMPLEMENTABLE".equals(reasonCode)) return "job.concept-portfolio.trace.excluded-legal";
            return "job.concept-portfolio.trace.excluded";
        }
        if ("COMPLETED".equals(action)) return "job.concept-portfolio.trace.ai-completed";
        if (stage.startsWith("LEGAL")) {
            if ("REVIEWED".equals(action)) return "job.concept-portfolio.trace.legal-reviewed";
            return "job.concept-portfolio.trace.legal";
        }
        if ("PLANNING".equals(stage) || "PLAN_VALIDATING".equals(stage)
                || "SEED_ANALYZING".equals(stage)) {
            return "job.concept-portfolio.trace.directions";
        }
        if ("EXPANDING".equals(stage) || "CANDIDATE_VALIDATING".equals(stage)) {
            return "job.concept-portfolio.trace.proposals";
        }
        return "job.concept-portfolio.trace.conditions";
    }
}
