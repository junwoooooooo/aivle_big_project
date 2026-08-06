package com.aivle.backend.journey.brief;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.journey.conversation.IdeaConversation;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "opportunity_brief_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpportunityBriefVersion extends BaseEntity {
    public enum State { DRAFT, CONFIRMED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "conversation_id", nullable = false) private IdeaConversation conversation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "based_on_version_id") private OpportunityBriefVersion basedOnVersion;
    @Column(nullable = false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(nullable = false, length = 71) private String snapshotHash;
    private LocalDateTime confirmedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id") private TaskRun taskRun;

    public static OpportunityBriefVersion draft(Project project, IdeaConversation conversation,
            OpportunityBriefVersion basedOnVersion, int versionNumber, String snapshotJson, String snapshotHash) {
        if (!conversation.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("conversation must belong to project");
        }
        if (basedOnVersion != null && !basedOnVersion.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("base brief must belong to project");
        }
        if (versionNumber <= 0) throw new IllegalArgumentException("version number must be positive");
        if (snapshotHash == null || !snapshotHash.startsWith("sha256:")) {
            throw new IllegalArgumentException("canonical snapshot hash is required");
        }
        OpportunityBriefVersion value = new OpportunityBriefVersion();
        value.project = project;
        value.conversation = conversation;
        value.basedOnVersion = basedOnVersion;
        value.versionNumber = versionNumber;
        value.state = State.DRAFT;
        value.snapshotJson = snapshotJson;
        value.snapshotHash = snapshotHash;
        return value;
    }

    public void linkTaskRun(TaskRun run) {
        if (run != null && !run.getProject().getId().equals(project.getId())) throw new IllegalArgumentException("task project mismatch");
        if (taskRun != null && !taskRun.getId().equals(run == null ? null : run.getId())) throw new IllegalStateException("brief task link is immutable");
        taskRun = run;
    }

    public void confirm(LocalDateTime now) {
        if (state != State.DRAFT) throw new IllegalStateException("only draft brief can be confirmed");
        state = State.CONFIRMED;
        confirmedAt = now;
    }
}
