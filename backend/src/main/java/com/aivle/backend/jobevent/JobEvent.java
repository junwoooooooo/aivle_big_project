package com.aivle.backend.jobevent;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobEvent extends BaseEntity {
    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED, NEEDS_INPUT, BLOCKED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64) private String jobId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id") private TaskRun taskRun;
    @Column(nullable = false, length = 50) private String stage;
    @Column(nullable = false, length = 80) private String eventType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, length = 120) private String messageKey;
    @Column(nullable = false, columnDefinition = "TEXT") private String messageParamsJson;
    @Column(length = 80) private String technicalCode;
    @Column(nullable = false) private long sequence;
    @Column(nullable = false) private LocalDateTime occurredAt;

    public static JobEvent create(String jobId, Project project, TaskRun taskRun, String stage,
            String eventType, Status status, String messageKey, String messageParamsJson,
            String technicalCode, long sequence, LocalDateTime occurredAt) {
        requireText(jobId, "job id");
        requireText(stage, "stage");
        requireText(eventType, "event type");
        requireText(messageKey, "message key");
        requireText(messageParamsJson, "message params");
        if (project == null) throw new IllegalArgumentException("event project is required");
        if (status == null) throw new IllegalArgumentException("event status is required");
        if (occurredAt == null) throw new IllegalArgumentException("event occurrence time is required");
        if (sequence <= 0) throw new IllegalArgumentException("event sequence must be positive");
        if (taskRun != null && !taskRun.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("task run must belong to event project");
        }
        JobEvent value = new JobEvent();
        value.jobId = jobId;
        value.project = project;
        value.taskRun = taskRun;
        value.stage = stage;
        value.eventType = eventType;
        value.status = status;
        value.messageKey = messageKey;
        value.messageParamsJson = messageParamsJson;
        value.technicalCode = technicalCode;
        value.sequence = sequence;
        value.occurredAt = occurredAt;
        return value;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
