package com.aivle.backend.journey.boundary;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.journey.brief.OpportunityBriefVersion;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "regulatory_boundary_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryBoundaryRun extends BaseEntity {
    public enum State {
        QUEUED, CLASSIFYING, ROUTING, FETCHING_EVIDENCE, SCREENING,
        NORMALIZING_RULES, CHECKING_CONFLICTS, READY, NEEDS_INPUT,
        BLOCKED, FAILED, STALE
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "brief_version_id", nullable = false) private OpportunityBriefVersion briefVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id") private TaskRun taskRun;
    @Column(nullable = false, length = 71) private String inputSnapshotHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(length = 80) private String errorCode;
    private LocalDateTime completedAt;

    public static RegulatoryBoundaryRun queued(Project project, OpportunityBriefVersion briefVersion,
            TaskRun taskRun, String inputSnapshotHash) {
        if (briefVersion.getState() != OpportunityBriefVersion.State.CONFIRMED) {
            throw new IllegalArgumentException("boundary run requires a confirmed brief");
        }
        if (!briefVersion.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("brief must belong to project");
        }
        if (taskRun != null && !taskRun.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("task run must belong to project");
        }
        if (inputSnapshotHash == null || !inputSnapshotHash.startsWith("sha256:")) {
            throw new IllegalArgumentException("canonical input snapshot hash is required");
        }
        RegulatoryBoundaryRun value = new RegulatoryBoundaryRun();
        value.project = project;
        value.briefVersion = briefVersion;
        value.taskRun = taskRun;
        value.inputSnapshotHash = inputSnapshotHash;
        value.state = State.QUEUED;
        return value;
    }

    public void start() {
        requireState(State.QUEUED);
        state = State.CLASSIFYING;
    }

    public void advance(State next) {
        if (next == null || next.ordinal() <= state.ordinal()
                || next.ordinal() > State.CHECKING_CONFLICTS.ordinal()) {
            throw new IllegalStateException("invalid boundary run stage transition");
        }
        state = next;
    }

    public void complete(State terminal, LocalDateTime now) {
        if (state != State.CHECKING_CONFLICTS
                || !java.util.Set.of(State.READY, State.NEEDS_INPUT, State.BLOCKED).contains(terminal)) {
            throw new IllegalStateException("invalid boundary run terminal transition");
        }
        state = terminal;
        errorCode = null;
        completedAt = now;
    }

    /** Compatibility helper for the G1 foundation service. */
    public void succeed(LocalDateTime now) {
        if (state == State.CLASSIFYING) state = State.CHECKING_CONFLICTS;
        complete(State.READY, now);
    }

    public void fail(String code, LocalDateTime now) {
        if (state == State.READY || state == State.NEEDS_INPUT || state == State.BLOCKED
                || state == State.FAILED || state == State.STALE) {
            throw new IllegalStateException("invalid boundary run state transition");
        }
        if (code == null || code.isBlank()) throw new IllegalArgumentException("failure code is required");
        state = State.FAILED;
        errorCode = code;
        completedAt = now;
    }

    public void markStale() {
        if (state == State.READY || state == State.NEEDS_INPUT || state == State.BLOCKED) state = State.STALE;
    }

    public void retryQueued() {
        if (state == State.READY || state == State.NEEDS_INPUT || state == State.BLOCKED || state == State.STALE)
            throw new IllegalStateException("terminal boundary cannot be requeued");
        state = State.QUEUED;
        errorCode = null;
        completedAt = null;
    }

    private void requireState(State expected) {
        if (state != expected) throw new IllegalStateException("invalid boundary run state transition");
    }
}
