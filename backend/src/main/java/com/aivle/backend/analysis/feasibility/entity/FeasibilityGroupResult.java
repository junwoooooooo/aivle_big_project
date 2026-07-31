package com.aivle.backend.analysis.feasibility.entity;

import com.aivle.backend.common.entity.AnalysisType;
import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import static com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;

/**
 * 시장·비즈니스 모델·기술 운영 묶음 단위 결과. assessment당 정확히 3행.
 *
 * <p>{@code score}와 {@code verdict}는 {@code FeasibilityScorePolicy}가 묶음 가중치로
 * 정규화해 계산한 값이고, 나머지 텍스트는 AI 서술이다. 묶음 안에 점수 미상 차원이
 * 하나라도 있으면 {@code score}는 null이다.
 */
@Entity @Table(name = "feasibility_group_results")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeasibilityGroupResult extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feasibility_assessment_id", nullable = false)
    private FeasibilityAssessment feasibilityAssessment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AnalysisType analysisType;
    @Column(nullable = false) private Integer displayOrder;
    private Integer score;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Verdict verdict;
    @Column(columnDefinition = "TEXT") private String headline;
    @Column(columnDefinition = "TEXT") private String summary;
    @Column(columnDefinition = "TEXT") private String strengthsJson;
    @Column(columnDefinition = "TEXT") private String risksJson;
    @Column(columnDefinition = "TEXT") private String nextFocus;

    public static FeasibilityGroupResult create(
        FeasibilityAssessment assessment, AnalysisType analysisType, int order,
        Integer score, Verdict verdict, String headline, String summary,
        String strengthsJson, String risksJson, String nextFocus
    ) {
        FeasibilityGroupResult value = new FeasibilityGroupResult();
        value.feasibilityAssessment = assessment;
        value.analysisType = analysisType;
        value.displayOrder = order;
        value.score = score;
        value.verdict = verdict;
        value.headline = headline;
        value.summary = summary;
        value.strengthsJson = strengthsJson;
        value.risksJson = risksJson;
        value.nextFocus = nextFocus;
        return value;
    }
}
