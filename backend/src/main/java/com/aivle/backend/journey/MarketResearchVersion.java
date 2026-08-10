package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 물질화된 시장조사·BM 결과.
 *
 * <p><b>결과를 쪼개지 않는다.</b> {@code resultJson} 이 정본이고, 아래 스칼라는
 * 목록·배지에서 훑기 위한 <b>사본</b>이다. 쪼개면 스키마가 계약과 DB 두 곳에 생기고
 * 계약이 바뀔 때마다 마이그레이션이 따라와야 한다.
 *
 * <p>{@code caveatCount} 를 굳이 컬럼으로 두는 이유: <b>경계가 0으로 떨어지는 것을
 * 눈으로 보기 위해서다.</b> 이 프로젝트가 반복해서 당한 실패가 「값은 맞는데 경계가 사라진」
 * 상태이고, 그건 JSON 안에 묻혀 있으면 아무도 안 본다.
 */
@Entity @Table(name = "market_research_versions") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketResearchVersion extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_run_id", nullable = false) private MarketResearchRun sourceRun;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private MarketResearchRun.Kind kind;
    @Column(nullable = false) private Integer versionNumber;

    // ⚠ `@Lob` 을 쓰지 않는다. Postgres 에서 `@Lob String` 은 `oid`(대용량 객체)를 기대하는데
    //   V2 는 `TEXT` 로 만든다 → `ddl-auto=validate` 가 부팅에서 죽는다(실측: 판 ㉝ 스모크).
    //   저장소의 다른 JSON 칸(`AiTaskResult.resultJson` 등)과 같은 방식으로 맞춘다.
    @Column(nullable = false, columnDefinition = "TEXT") private String resultJson;

    private Integer filledCount;
    private Integer partialCount;
    private Integer missingCount;
    @Column(length = 20) private String decision;
    @Column(length = 10) private String confidence;
    @Column(nullable = false) private Integer evidenceCount;
    @Column(nullable = false) private Integer caveatCount;

    public static MarketResearchVersion of(Project project, MarketResearchRun run, int versionNumber,
                                           String resultJson, Summary summary) {
        MarketResearchVersion value = new MarketResearchVersion();
        value.project = project;
        value.sourceRun = run;
        value.kind = run.getKind();
        value.versionNumber = versionNumber;
        value.resultJson = resultJson;
        value.filledCount = summary.filled();
        value.partialCount = summary.partial();
        value.missingCount = summary.missing();
        value.decision = summary.decision();
        value.confidence = summary.confidence();
        value.evidenceCount = summary.evidenceCount();
        value.caveatCount = summary.caveatCount();
        return value;
    }

    /** 목록용 스칼라 묶음. 판정의 근거가 아니다 — 판정은 언제나 {@code resultJson} 을 읽는다. */
    public record Summary(Integer filled, Integer partial, Integer missing,
                          String decision, String confidence,
                          int evidenceCount, int caveatCount) { }
}
