package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시장조사·BM 실행 1회. {@link LegalPrecheckRun} 의 형제(패턴 B).
 *
 * <p>한 TaskType 에 <b>두 모드</b>가 있고 {@link Kind} 가 그것을 가른다.
 * {@code BM} 은 {@code sourceRun} 으로 1단계 결과를 가리킨다 —
 * <b>결과를 통째로 넘기지 않는 이유</b>는 그 안에 부동소수점이 31개 있고,
 * {@code CanonicalInputHasher} 가 taskInput 의 float 를 런타임에 거부하기 때문이다.
 */
@Entity @Table(name = "market_research_runs") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketResearchRun extends BaseEntity {

    /** FULL = 1단계(시장조사) · BM = 2단계(캔버스). */
    public enum Kind { FULL, BM }
    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Kind kind;
    /** 2단계가 가리키는 1단계 실행. FULL 이면 null. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_run_id") private MarketResearchRun sourceRun;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id", nullable = false) private TaskRun taskRun;
    @Column(nullable = false, length = 71) private String inputSnapshotHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(length = 80) private String errorCode;
    private LocalDateTime completedAt;

    public static MarketResearchRun create(Project project, Kind kind, MarketResearchRun sourceRun,
                                           TaskRun taskRun, String inputHash) {
        MarketResearchRun value = new MarketResearchRun();
        value.project = project;
        value.kind = kind;
        value.sourceRun = sourceRun;
        value.taskRun = taskRun;
        value.inputSnapshotHash = inputHash;
        value.state = State.QUEUED;
        return value;
    }

    public void running() { if (state == State.QUEUED) state = State.RUNNING; }
    public void succeed() { state = State.SUCCEEDED; errorCode = null; completedAt = LocalDateTime.now(); }
    public void fail(String errorCode) {
        state = State.FAILED;
        this.errorCode = errorCode;
        completedAt = LocalDateTime.now();
    }
    public boolean terminal() { return state == State.SUCCEEDED || state == State.FAILED; }
}
