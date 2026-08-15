package com.aivle.backend.jobevent;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobEventPublisher {
    private final ProjectRepository projects;
    private final TaskRunRepository taskRuns;
    private final JobEventRepository events;
    private final JobEventPayloadPolicy payloadPolicy;
    private final JobEventStreamService streams;
    private final ProjectEventStreamService projectStreams;
    private final ObjectMapper mapper;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public JobEventPublisher(ProjectRepository projects, TaskRunRepository taskRuns,
            JobEventRepository events, JobEventPayloadPolicy payloadPolicy,
            JobEventStreamService streams, ProjectEventStreamService projectStreams,
            ObjectMapper mapper, Clock jobClock) {
        this.projects = projects;
        this.taskRuns = taskRuns;
        this.events = events;
        this.payloadPolicy = payloadPolicy;
        this.streams = streams;
        this.projectStreams = projectStreams;
        this.mapper = mapper;
        this.clock = jobClock;
    }

    public JobEventPublisher(ProjectRepository projects, TaskRunRepository taskRuns,
            JobEventRepository events, JobEventPayloadPolicy payloadPolicy,
            JobEventStreamService streams, ObjectMapper mapper, Clock jobClock) {
        this(projects, taskRuns, events, payloadPolicy, streams, null, mapper, jobClock);
    }

    @Transactional
    public JobEventView publish(Command command) {
        validate(command);
        Project project = projects.findByIdForUpdate(command.projectId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        JobEvent latestForJob = events.findTopByJobIdAndDeletedAtIsNullOrderBySequenceDesc(command.jobId())
            .orElse(null);
        if (latestForJob != null && !latestForJob.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("job id already belongs to another project");
        }
        if (latestForJob != null && terminal(latestForJob.getStatus())) {
            throw new IllegalStateException("TERMINAL_JOB_EVENT_IMMUTABLE");
        }
        TaskRun taskRun = command.taskRunId() == null ? null : taskRuns.findById(command.taskRunId())
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (taskRun != null && !taskRun.getProject().getId().equals(project.getId())) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        long sequence = latestForJob == null ? 1 : latestForJob.getSequence() + 1;
        String paramsJson = payloadPolicy.serialize(
            command.messageKey(), command.messageParams(), command.technicalCode());
        JobEvent event = events.save(JobEvent.create(
            command.jobId(), project, taskRun, command.stage(), command.eventType(),
            command.status(), command.messageKey(), paramsJson, command.technicalCode(),
            sequence, LocalDateTime.now(clock)));
        JobEventView view = JobEventView.from(event, mapper);
        afterCommit(() -> {
            streams.publish(view);
            if (projectStreams != null) projectStreams.publish(view);
        });
        return view;
    }

    private void validate(Command command) {
        if (command == null || command.projectId() == null) {
            throw new IllegalArgumentException("job event project is required");
        }
        requireLength(command.jobId(), "job id", 64);
        requireLength(command.stage(), "job event stage", 50);
        requireLength(command.eventType(), "job event type", 80);
        if (command.status() == null) throw new IllegalArgumentException("job event status is required");
    }

    private boolean terminal(JobEvent.Status status) {
        return status == JobEvent.Status.COMPLETED || status == JobEvent.Status.NEEDS_INPUT
            || status == JobEvent.Status.FAILED || status == JobEvent.Status.BLOCKED;
    }

    private void requireLength(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public record Command(
        Long projectId,
        String jobId,
        String taskRunId,
        String stage,
        String eventType,
        JobEvent.Status status,
        String messageKey,
        Map<String, ?> messageParams,
        String technicalCode
    ) { }
}
