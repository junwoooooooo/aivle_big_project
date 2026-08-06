package com.aivle.backend.analysis.feasibility.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import static com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;

@Entity @Table(name = "feasibility_dimension_results")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeasibilityDimensionResult extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feasibility_assessment_id", nullable = false)
    private FeasibilityAssessment feasibilityAssessment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 60) private DimensionCode dimensionCode;
    @Column(nullable = false) private Integer displayOrder;
    private Integer score;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Confidence confidence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DimensionStatus status;
    @Column(nullable = false, columnDefinition = "TEXT") private String finding;
    @Column(nullable = false, columnDefinition = "TEXT") private String rationale;
    @Column(nullable = false, columnDefinition = "TEXT") private String strengthsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String risksJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String assumptionsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String evidenceJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String sourceSectionCodesJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String legalFindingIdsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String recommendedActionsJson;

    public static FeasibilityDimensionResult create(
        FeasibilityAssessment assessment, DimensionCode code, int order, Integer score,
        Confidence confidence, DimensionStatus status, String finding, String rationale,
        String strengthsJson, String risksJson, String assumptionsJson, String evidenceJson,
        String sourceSectionsJson, String legalFindingIdsJson, String actionsJson
    ) {
        FeasibilityDimensionResult value = new FeasibilityDimensionResult();
        value.feasibilityAssessment = assessment;
        value.dimensionCode = code;
        value.displayOrder = order;
        value.score = score;
        value.confidence = confidence;
        value.status = status;
        value.finding = finding;
        value.rationale = rationale;
        value.strengthsJson = strengthsJson;
        value.risksJson = risksJson;
        value.assumptionsJson = assumptionsJson;
        value.evidenceJson = evidenceJson;
        value.sourceSectionCodesJson = sourceSectionsJson;
        value.legalFindingIdsJson = legalFindingIdsJson;
        value.recommendedActionsJson = actionsJson;
        return value;
    }
}
