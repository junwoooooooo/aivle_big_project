package com.aivle.backend.taskrun.service;

import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.common.entity.ProjectStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.repository.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class TaskRunService {
    private final TaskRunRepository runs;
    private final TaskAttemptRepository attempts;
    private final TaskResultRepository results;
    private final ProjectRepository projects;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final CanonicalInputHasher canonicalInputHasher;
    private final ServicePolicyService servicePolicy;

    public TaskRunService(TaskRunRepository runs, TaskAttemptRepository attempts, TaskResultRepository results, ProjectRepository projects, Optional<Clock> clock, ObjectMapper mapper, CanonicalInputHasher canonicalInputHasher, ServicePolicyService servicePolicy) {
        this.runs = runs; this.attempts = attempts; this.results = results; this.projects = projects; this.clock = clock.orElse(Clock.systemUTC()); this.mapper = mapper; this.canonicalInputHasher = canonicalInputHasher; this.servicePolicy = servicePolicy;
    }

    @Transactional
    public TaskRun create(Long ownerId, Long projectId, TaskType type, String subjectType, String subjectId,
                          String input, String hash, String idempotencyKey, String correlationId, int maxAttempts) {
        Project project = projects.findByIdForUpdate(projectId)
            .filter(value -> value.getOwner().getId().equals(ownerId)).orElseThrow(this::notFound);
        if (project.getStatus() == ProjectStatus.ARCHIVED) throw new TaskRunFailure("CAPABILITY_NOT_AVAILABLE", "PROJECT_ARCHIVED", HttpStatus.CONFLICT, false);
        try { servicePolicy.requireWriteAvailableForUser(ownerId); }
        catch (BusinessException blocked) { throw new TaskRunFailure("POLICY_BLOCKED", "MAINTENANCE_MODE", HttpStatus.SERVICE_UNAVAILABLE, false); }
        validateCreation(input, hash, idempotencyKey, correlationId, maxAttempts);
        if (!canonicalInputHasher.hash(type, "1.0", "ko-KR", input).equals(hash))
            throw new TaskRunFailure("VALIDATION_ERROR", "CANONICAL_INPUT_HASH_MISMATCH", HttpStatus.BAD_REQUEST, false);
        String scope = type.name() + ":" + subjectType + ":" + subjectId;
        Optional<TaskRun> replay = runs.findByProjectIdAndIdempotencyScopeAndIdempotencyKey(projectId, scope, idempotencyKey);
        if (replay.isPresent()) {
            if (replay.get().getInputHash().equals(hash)) return replay.get();
            throw new TaskRunFailure("IDEMPOTENCY_CONFLICT", "REQUEST_HASH_MISMATCH", HttpStatus.CONFLICT, false);
        }
        if (runs.findFirstByProjectIdAndTaskTypeAndSubjectTypeAndSubjectIdAndInputHashAndStateIn(
            projectId, type, subjectType, subjectId, hash,
            List.of(TaskRunState.QUEUED, TaskRunState.READY, TaskRunState.RUNNING)).isPresent()) {
            throw new TaskRunFailure("TASK_ALREADY_RUNNING", "SAME_INPUT_ACTIVE", HttpStatus.CONFLICT, false);
        }
        TaskRun created = TaskRun.create(project, type, subjectType, subjectId, input, hash, idempotencyKey, correlationId, maxAttempts);
        created.scheduleInitial(LocalDateTime.now(clock));
        return runs.save(created);
    }

    @Transactional(readOnly = true)
    public TaskRun getOwned(Long ownerId, Long projectId, String id) { return runs.findOwned(ownerId, projectId, id).orElseThrow(this::notFound); }

    @Transactional(readOnly = true)
    public TaskRun getOwnedForWorker(String id) { return runs.findById(id).orElseThrow(this::notFound); }

    @Transactional(readOnly = true)
    public TaskRunWorkerContext workerContext(String id) {
        return runs.findWorkerContext(id).orElseThrow(this::notFound);
    }

    @Transactional
    public Claim claimNext(String workerId, Duration lease, Duration timeout) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<TaskRun> candidates = runs.findClaimable(List.of(TaskRunState.QUEUED, TaskRunState.READY), now, PageRequest.of(0, 1));
        if (candidates.isEmpty()) return null;
        TaskRun run = candidates.get(0);
        TaskAttempt attempt = run.getCurrentAttemptId() == null ? null : attempts.findById(run.getCurrentAttemptId()).orElse(null);
        if (attempt != null && attempt.getState() == TaskAttemptState.CREATED) {
            attempt.claim(workerId, now, now.plus(lease), now.plus(timeout));
        } else {
            if (run.getAttemptCount() >= run.getMaxAttempts()) { run.exhaustAttempts(now); return null; }
            attempt = TaskAttempt.claim(run, workerId, now, now.plus(lease), now.plus(timeout));
            attempts.save(attempt);
        }
        return new Claim(run.getId(), attempt.getId(), attempt.getClaimToken());
    }

    @Transactional
    public Claim claimNext(TaskType taskType, String workerId, Duration lease, Duration timeout) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<TaskRun> candidates = runs.findClaimableByType(taskType,
            List.of(TaskRunState.QUEUED, TaskRunState.READY), now, PageRequest.of(0, 1));
        if (candidates.isEmpty()) return null;
        TaskRun run = candidates.get(0);
        TaskAttempt attempt = run.getCurrentAttemptId() == null ? null : attempts.findById(run.getCurrentAttemptId()).orElse(null);
        if (attempt != null && attempt.getState() == TaskAttemptState.CREATED) attempt.claim(workerId, now, now.plus(lease), now.plus(timeout));
        else { if (run.getAttemptCount() >= run.getMaxAttempts()) { run.exhaustAttempts(now); return null; }
            attempt = TaskAttempt.claim(run, workerId, now, now.plus(lease), now.plus(timeout)); attempts.save(attempt); }
        return new Claim(run.getId(), attempt.getId(), attempt.getClaimToken());
    }

    @Transactional
    public Claim claim(String runId, String workerId, Duration lease, Duration timeout) {
        TaskRun run = runs.findLocked(runId).orElseThrow(this::notFound);
        if (run.getState() != TaskRunState.QUEUED && run.getState() != TaskRunState.READY)
            throw new TaskRunFailure("TASK_ALREADY_RUNNING", "TASK_NOT_CLAIMABLE", HttpStatus.CONFLICT, false);
        LocalDateTime now = LocalDateTime.now(clock);
        TaskAttempt attempt = run.getCurrentAttemptId() == null ? null
            : attempts.findById(run.getCurrentAttemptId()).orElse(null);
        if (attempt != null && attempt.getState() == TaskAttemptState.CREATED) {
            attempt.claim(workerId, now, now.plus(lease), now.plus(timeout));
        } else {
            if (run.getAttemptCount() >= run.getMaxAttempts())
                throw new TaskRunFailure("CAPABILITY_NOT_AVAILABLE", "ATTEMPT_LIMIT_EXCEEDED", HttpStatus.CONFLICT, false);
            attempt = TaskAttempt.claim(run, workerId, now, now.plus(lease), now.plus(timeout));
            attempts.save(attempt);
        }
        return new Claim(run.getId(), attempt.getId(), attempt.getClaimToken());
    }

    @Transactional
    public void heartbeat(String runId, String attemptId, String claimToken, Duration lease) {
        TaskAttempt attempt = attempts.findByIdAndTaskRunId(attemptId, runId).orElseThrow(this::notFound);
        LocalDateTime now = LocalDateTime.now(clock); attempt.heartbeat(claimToken, now, now.plus(lease));
    }

    @Transactional
    public void startExecution(String runId, String attemptId, String claimToken) {
        TaskAttempt attempt = attempts.findByIdAndTaskRunId(attemptId, runId).orElseThrow(this::notFound);
        attempt.start(claimToken, LocalDateTime.now(clock));
    }

    @Transactional(noRollbackFor = TaskRunFailure.class)
    public TaskResult adopt(String runId, String attemptId, String claimToken, String payload, String hash, String schemaVersion) {
        TaskRun run = runs.findLocked(runId).orElseThrow(this::notFound);
        TaskAttempt attempt = attempts.findByIdAndTaskRunId(attemptId, runId).orElseThrow(this::notFound);
        LocalDateTime now = LocalDateTime.now(clock);
        if (run.getState() != TaskRunState.RUNNING || run.getFinalResultId() != null || !attemptId.equals(run.getCurrentAttemptId()))
            return reject(run, attempt, payload, schemaVersion, "LATE_OR_DUPLICATE_RESULT", HttpStatus.CONFLICT);
        try { attempt.assertCompletable(claimToken, now); }
        catch (IllegalStateException | IllegalArgumentException invalid) { return reject(run, attempt, payload, schemaVersion, "STALE_CLAIM", HttpStatus.CONFLICT); }
        if (!run.getInputHash().equals(hash)) return reject(run, attempt, payload, schemaVersion, "HASH_MISMATCH", HttpStatus.BAD_GATEWAY);
        attempt.succeed(claimToken, now);
        TaskResult result = results.save(TaskResult.adopted(run, attempt, payload, sha256(payload), schemaVersion, now)); run.succeed(result.getId(), now); return result;
    }

    @Transactional
    public void fail(String runId, String attemptId, String claimToken, String code, String reason, boolean retryable) {
        TaskRun run = runs.findLocked(runId).orElseThrow(this::notFound); TaskAttempt attempt = attempts.findByIdAndTaskRunId(attemptId, runId).orElseThrow(this::notFound);
        if (run.terminal()) return;
        LocalDateTime now = LocalDateTime.now(clock); attempt.fail(claimToken, code, reason, retryable, now); run.fail(mapPublic(code, reason), retryable, now);
    }

    @Transactional
    public void failWithLegalAutoRetry(String runId, String attemptId, String claimToken,
            String code, String reason, boolean retryable) {
        TaskRun run = runs.findLocked(runId).orElseThrow(this::notFound);
        TaskAttempt attempt = attempts.findByIdAndTaskRunId(attemptId, runId).orElseThrow(this::notFound);
        if (run.terminal()) return;
        LocalDateTime now = LocalDateTime.now(clock);
        attempt.fail(claimToken, code, reason, retryable, now);
        run.fail(mapPublic(code, reason), retryable, now);
        if (run.getTaskType() == TaskType.IDEA_LEGAL_PRECHECK && run.isRetryable()) {
            run.queueRetry(now);
            attempts.save(TaskAttempt.pending(run, now.plusMinutes(2)));
        }
    }

    @Transactional
    public void rejectAndFail(String runId, String attemptId, String claimToken, String payload,
                              String schemaVersion, String reason) {
        TaskRun run = runs.findLocked(runId).orElseThrow(this::notFound);
        TaskAttempt attempt = attempts.findByIdAndTaskRunId(attemptId, runId).orElseThrow(this::notFound);
        if (run.terminal()) return;
        LocalDateTime now = LocalDateTime.now(clock);
        try { attempt.assertCompletable(claimToken, now); }
        catch (IllegalStateException | IllegalArgumentException stale) { return; }
        results.save(TaskResult.rejected(run, attempt, payload, sha256(payload), schemaVersion,
            reason, now));
        attempt.fail(claimToken, "RESULT_SCHEMA_INVALID", reason, false, now);
        run.fail("AI_RESULT_INVALID", false, now);
    }

    @Transactional
    public TaskRun retry(Long ownerId, Long projectId, String id, String idempotencyKey) {
        TaskRun owned = getOwned(ownerId, projectId, id); TaskRun run = runs.findLocked(owned.getId()).orElseThrow(this::notFound);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) throw new TaskRunFailure("VALIDATION_ERROR", "IDEMPOTENCY_KEY_INVALID", HttpStatus.BAD_REQUEST, false);
        if (run.retryReplay(idempotencyKey)) return run;
        if (run.retryKeyConflicts(idempotencyKey)) throw new TaskRunFailure("IDEMPOTENCY_CONFLICT", "RETRY_KEY_CONFLICT", HttpStatus.CONFLICT, false);
        if (!run.isRetryable()) throw new TaskRunFailure("CAPABILITY_NOT_AVAILABLE", "TASK_NOT_RETRYABLE", HttpStatus.CONFLICT, false);
        LocalDateTime now = LocalDateTime.now(clock); run.queueRetry(now); run.recordRetryKey(idempotencyKey); attempts.save(TaskAttempt.pending(run, now.plusMinutes(2))); return run;
    }

    @Transactional
    public TaskRun cancel(Long ownerId, Long projectId, String id) { TaskRun owned = getOwned(ownerId, projectId, id); TaskRun run = runs.findLocked(owned.getId()).orElseThrow(this::notFound); LocalDateTime now=LocalDateTime.now(clock); if(run.getCurrentAttemptId()!=null)attempts.findById(run.getCurrentAttemptId()).ifPresent(a->a.cancel(now)); run.cancel(now); return run; }

    @Transactional
    public int recoverExpired(Duration staleAfter) {
        return recoverExpired(staleAfter, null);
    }

    @Transactional
    public int recoverExpired(Duration staleAfter, List<TaskType> taskTypes) {
        return recoverExpiredTaskIds(staleAfter, taskTypes).size();
    }

    @Transactional
    public List<String> recoverExpiredTaskIds(Duration staleAfter, List<TaskType> taskTypes) {
        LocalDateTime now = LocalDateTime.now(clock); int recovered = 0;
        java.util.ArrayList<String> recoveredIds = new java.util.ArrayList<>();
        List<TaskAttemptState> states = List.of(TaskAttemptState.CLAIMED, TaskAttemptState.RUNNING);
        List<String> expired = taskTypes == null ? attempts.findExpiredIds(states, now.minus(staleAfter))
            : attempts.findExpiredIdsByTaskTypes(taskTypes, states, now.minus(staleAfter));
        for (String attemptId : expired) {
            TaskAttempt observed = attempts.findById(attemptId).orElse(null);
            if (observed == null) continue;
            TaskRun run = runs.findLocked(observed.getTaskRun().getId()).orElse(null);
            TaskAttempt attempt = attempts.findLocked(attemptId).orElse(null);
            if (run != null && attempt != null && attemptId.equals(run.getCurrentAttemptId())
                && run.getState() == TaskRunState.RUNNING && attempt.leaseExpired(now)) {
                attempt.timeOut(now); run.recoverAfterLeaseExpiry(now); recovered++; recoveredIds.add(run.getId());
            }
        }
        return recoveredIds;
    }

    @Transactional
    public boolean scheduleRetry(String runId, Duration backoff) {
        TaskRun run = runs.findLocked(runId).orElseThrow(this::notFound);
        if (!run.isRetryable()) return false;
        LocalDateTime eligibleAt = LocalDateTime.now(clock).plus(backoff);
        run.queueRetry(eligibleAt);
        attempts.save(TaskAttempt.pending(run, eligibleAt));
        return true;
    }

    private String mapPublic(String internal, String reason) { if ("AI_CONFIGURATION_INVALID".equals(reason)) return "AI_CONFIGURATION_INVALID"; return switch (internal) {
        case "PAYLOAD_TOO_LARGE" -> "PAYLOAD_TOO_LARGE";
        case "DEADLINE_EXCEEDED" -> "TASK_TIMEOUT";
        case "INVALID_REQUEST", "UNSUPPORTED_CONTRACT_VERSION", "UNSUPPORTED_TASK_TYPE",
             "UNSUPPORTED_TASK_SCHEMA_VERSION", "RESULT_SCHEMA_INVALID" -> "AI_RESULT_INVALID";
        default -> "AI_SERVICE_UNAVAILABLE";
    }; }
    private TaskResult reject(TaskRun run, TaskAttempt attempt, String payload, String schemaVersion, String reason, HttpStatus status) {
        results.save(TaskResult.rejected(run, attempt, payload, sha256(payload), schemaVersion, reason, LocalDateTime.now(clock)));
        throw new TaskRunFailure("AI_RESULT_INVALID", reason, status, false);
    }
    private void validateCreation(String input, String hash, String key, String correlation, int maxAttempts) {
        if (input == null || input.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024 || hash == null || !hash.matches("sha256:[0-9a-f]{64}")
            || key == null || key.isBlank() || key.length() > 128 || correlation == null || correlation.isBlank() || correlation.length() > 128
            || maxAttempts < 1 || maxAttempts > 20) {
            throw new TaskRunFailure("VALIDATION_ERROR", "TASK_RUN_INPUT_INVALID", HttpStatus.BAD_REQUEST, false);
        }
        try { mapper.readTree(input); } catch (RuntimeException invalidJson) { throw new TaskRunFailure("VALIDATION_ERROR", "INPUT_JSON_INVALID", HttpStatus.BAD_REQUEST, false); }
    }
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private TaskRunFailure notFound() { return new TaskRunFailure("RESOURCE_NOT_FOUND", "TASK_RUN_NOT_FOUND", HttpStatus.NOT_FOUND, false); }
    public record Claim(String taskRunId, String taskAttemptId, String claimToken) {}
}
