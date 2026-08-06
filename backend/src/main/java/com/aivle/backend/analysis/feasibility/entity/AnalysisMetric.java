package com.aivle.backend.analysis.feasibility.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity @Table(name = "analysis_metrics")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisMetric extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "feasibility_analysis_id", nullable = false) private FeasibilityAnalysis feasibilityAnalysis;
    @Column(nullable = false, length = 100) private String metricKey;
    @Column(nullable = false, length = 150) private String label;
    @Column(precision = 19, scale = 4) private BigDecimal numericValue;
    @Column(columnDefinition = "TEXT") private String textValue;
    @Column(length = 50) private String unit;
    private Integer displayOrder;
}
