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
@Table(name = "legal_review_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalReviewRun extends BaseEntity {
    public enum State { PENDING, RUNNING, SUCCEEDED, FAILED }
    public enum LegalStatus { PASS, PASS_WITH_CONDITIONS, REVISION_REQUIRED, PROHIBITED, INSUFFICIENT_INFORMATION, EXPERT_REVIEW_REQUIRED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_version_id", nullable = false) private IdeaVersion ideaVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id") private TaskRun taskRun;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Enumerated(EnumType.STRING) @Column(length = 40) private LegalStatus legalStatus;
    @Column(name = "result_json", columnDefinition = "TEXT") private String resultJson;
    @Column(nullable = false) private boolean sourceVerified;
    private LocalDateTime completedAt;

    public static LegalReviewRun pending(Project project, IdeaVersion ideaVersion) {
        LegalReviewRun value = new LegalReviewRun(); value.project = project; value.ideaVersion = ideaVersion;
        value.state = State.PENDING; value.sourceVerified = false; return value;
    }
    public void start(TaskRun taskRun) {
        this.taskRun = taskRun; this.state = State.RUNNING; this.legalStatus = null;
        this.resultJson = null; this.sourceVerified = false; this.completedAt = null;
    }
    public void succeed(LegalStatus status, String result) { this.legalStatus = status; this.resultJson = result; this.sourceVerified = false; this.state = State.SUCCEEDED; this.completedAt = LocalDateTime.now(); }
    public void fail() { this.state = State.FAILED; this.sourceVerified = false; this.completedAt = LocalDateTime.now(); }
}
