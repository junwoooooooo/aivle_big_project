package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 패널 트윈 조사 실행 1회. {@link MarketResearchRun} 의 형제(패턴 B)지만 <b>모드가 하나</b>다.
 *
 * <p>{@code sampleSize} 를 컬럼으로 두는 이유는 목록 때문이 아니라 <b>측정 한계 때문</b>이다.
 * 같은 자극이라도 n 이 다르면 답이 「못 잼」과 「이김」으로 갈린다 — 어떤 표본으로 잰
 * 결과인지가 값과 떨어지면 그 결과는 해석할 수 없다.
 */
@Entity @Table(name = "twin_survey_runs") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwinSurveyRun extends BaseEntity {

    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id", nullable = false) private TaskRun taskRun;
    @Column(name = "source_market_seed_snapshot_id", nullable = false, length = 64)
    private String sourceMarketSeedSnapshotId;
    @Column(name = "source_portfolio_selection_id", nullable = false)
    private Long sourcePortfolioSelectionId;
    @Column(name = "source_selection_revision", nullable = false)
    private Integer sourceSelectionRevision;
    @Column(nullable = false, length = 71) private String inputSnapshotHash;
    @Column(nullable = false) private Integer sampleSize;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(length = 80) private String errorCode;
    private LocalDateTime completedAt;

    public static TwinSurveyRun create(Project project, TaskRun taskRun, String inputHash, int sampleSize,
                                       String sourceSeedId, Long sourceSelectionId, int sourceRevision) {
        TwinSurveyRun value = new TwinSurveyRun();
        value.project = project;
        value.taskRun = taskRun;
        value.sourceMarketSeedSnapshotId = sourceSeedId;
        value.sourcePortfolioSelectionId = sourceSelectionId;
        value.sourceSelectionRevision = sourceRevision;
        value.inputSnapshotHash = inputHash;
        value.sampleSize = sampleSize;
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
}
