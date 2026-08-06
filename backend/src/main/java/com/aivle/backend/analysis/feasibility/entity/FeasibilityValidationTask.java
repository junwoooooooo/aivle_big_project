package com.aivle.backend.analysis.feasibility.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import static com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;

@Entity @Table(name = "feasibility_validation_tasks")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeasibilityValidationTask extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feasibility_assessment_id", nullable = false)
    private FeasibilityAssessment feasibilityAssessment;
    @Column(nullable = false, length = 100) private String taskCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 60) private DimensionCode dimensionCode;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(nullable = false, columnDefinition = "TEXT") private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ValidationPriority priority;
    @Column(nullable = false, columnDefinition = "TEXT") private String validationMethod;
    @Column(nullable = false, columnDefinition = "TEXT") private String expectedEvidence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ValidationTaskStatus status;
    @Column(nullable = false) private Integer displayOrder;

    public static FeasibilityValidationTask open(
        FeasibilityAssessment assessment, String code, DimensionCode dimension, String title,
        String description, String reason, ValidationPriority priority, String method,
        String expectedEvidence, int order
    ) {
        FeasibilityValidationTask value = new FeasibilityValidationTask();
        value.feasibilityAssessment = assessment;
        value.taskCode = code;
        value.dimensionCode = dimension;
        value.title = title;
        value.description = description;
        value.reason = reason;
        value.priority = priority;
        value.validationMethod = method;
        value.expectedEvidence = expectedEvidence;
        value.status = ValidationTaskStatus.OPEN;
        value.displayOrder = order;
        return value;
    }
}
