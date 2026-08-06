package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "legal_precheck_runs") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalPrecheckRun extends BaseEntity {
    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_origin_version_id", nullable = false) private IdeaOriginVersion ideaOriginVersion;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id", nullable = false) private TaskRun taskRun;
    @Column(nullable = false, length = 71) private String inputSnapshotHash;
    @Column(nullable = false, length = 40) private String registryVersion;
    @Column(nullable = false, length = 40) private String promptVersion;
    @Column(nullable = false, length = 20) private String schemaVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(length = 80) private String errorCode;
    private LocalDateTime completedAt;

    public static LegalPrecheckRun create(Project project, IdeaOriginVersion origin, TaskRun taskRun,
            String inputHash, String registryVersion, String promptVersion, String schemaVersion) {
        LegalPrecheckRun value = new LegalPrecheckRun(); value.project = project; value.ideaOriginVersion = origin;
        value.taskRun = taskRun; value.inputSnapshotHash = inputHash; value.registryVersion = registryVersion;
        value.promptVersion = promptVersion; value.schemaVersion = schemaVersion; value.state = State.QUEUED;
        return value;
    }
    public void running() { if (state == State.QUEUED) state = State.RUNNING; }
    public void queued() { if (state == State.FAILED) { state = State.QUEUED; errorCode = null; completedAt = null; } }
    public void succeed() { state = State.SUCCEEDED; errorCode = null; completedAt = LocalDateTime.now(); }
    public void fail(String errorCode) { state = State.FAILED; this.errorCode = errorCode; completedAt = LocalDateTime.now(); }
}
