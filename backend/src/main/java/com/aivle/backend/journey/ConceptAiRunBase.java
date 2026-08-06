package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;

@MappedSuperclass
@Getter
abstract class ConceptAiRunBase extends BaseEntity {
    enum State { PENDING, RUNNING, SUCCEEDED, FAILED }

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_version_id", nullable = false) private IdeaVersion ideaVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id") private TaskRun taskRun;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(name = "result_json", columnDefinition = "TEXT") private String resultJson;
    @Column(length = 100) private String error;
    private LocalDateTime completedAt;

    protected void initialize(Project project, IdeaVersion ideaVersion) {
        this.project = project; this.ideaVersion = ideaVersion; this.state = State.PENDING;
    }
    public void start(TaskRun taskRun) {
        this.taskRun = taskRun; this.state = State.RUNNING; this.resultJson = null; this.error = null; this.completedAt = null;
    }
    public void succeed(String resultJson) {
        this.resultJson = resultJson; this.error = null; this.state = State.SUCCEEDED; this.completedAt = LocalDateTime.now();
    }
    public void fail(String error) {
        this.error = error; this.state = State.FAILED; this.completedAt = LocalDateTime.now();
    }
}
