package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_interpretation_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaInterpretationRun extends BaseEntity {
    public enum State { PENDING, RUNNING, SUCCEEDED, FAILED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_id", nullable = false) private IdeaSource source;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id") private TaskRun taskRun;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(name = "result_json", columnDefinition = "TEXT") private String resultJson;
    @Column(length = 100) private String error;
    private LocalDateTime completedAt;

    public static IdeaInterpretationRun pending(Project project, IdeaSource source) {
        IdeaInterpretationRun value = new IdeaInterpretationRun(); value.project = project; value.source = source;
        value.state = State.PENDING; return value;
    }
    public void start(TaskRun taskRun) { this.taskRun = taskRun; this.state = State.RUNNING; }
    public void retrying() { this.state = State.RUNNING; this.error = null; this.completedAt = null; }
    public void succeed(String result) { this.resultJson = result; this.state = State.SUCCEEDED; this.completedAt = LocalDateTime.now(); }
    public void fail(String code) { this.error = code; this.state = State.FAILED; this.completedAt = LocalDateTime.now(); }
}
