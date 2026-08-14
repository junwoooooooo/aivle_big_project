package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 물질화된 트윈 조사 결과. {@link MarketResearchVersion} 과 같은 규칙을 따른다 —
 * <b>결과를 쪼개지 않는다.</b> {@code resultJson} 이 정본이고 스칼라는 사본이다.
 *
 * <p>{@code measurableCount} 를 세는 이유: 「못 잼」은 실패가 아니라 이 기능의 정직한
 * 산출이다. 세어 두지 않으면 못 잰 결과가 잰 결과처럼 목록에 앉는다.
 * {@code caveatCount} 는 경계 소실을 눈으로 보기 위한 것이고, 0 이면 계약이 이미 막았어야 한다.
 */
@Entity @Table(name = "twin_survey_versions") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwinSurveyVersion extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_run_id", nullable = false) private TwinSurveyRun sourceRun;
    @Column(nullable = false) private Integer versionNumber;

    // @Lob 을 쓰지 않는 이유는 MarketResearchVersion 과 같다 — Postgres 에서 oid 를 기대해
    // ddl-auto=validate 가 부팅에서 죽는다.
    @Column(nullable = false, columnDefinition = "TEXT") private String resultJson;

    @Column(nullable = false) private Integer sampleSize;
    @Column(nullable = false) private Integer pairCount;
    @Column(nullable = false) private Integer measurableCount;
    @Column(nullable = false) private Integer caveatCount;

    public static TwinSurveyVersion of(Project project, TwinSurveyRun run, int versionNumber,
                                       String resultJson, Summary summary) {
        TwinSurveyVersion value = new TwinSurveyVersion();
        value.project = project;
        value.sourceRun = run;
        value.versionNumber = versionNumber;
        value.resultJson = resultJson;
        value.sampleSize = summary.sampleSize();
        value.pairCount = summary.pairCount();
        value.measurableCount = summary.measurableCount();
        value.caveatCount = summary.caveatCount();
        return value;
    }

    /** 목록용 스칼라 묶음. 판정의 근거가 아니다 — 판정은 언제나 {@code resultJson} 을 읽는다. */
    public record Summary(int sampleSize, int pairCount, int measurableCount, int caveatCount) { }
}
