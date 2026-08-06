package com.aivle.backend.analysis.feasibility.entity;

import com.aivle.backend.common.entity.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "analysis_recommendations")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisRecommendation extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "feasibility_analysis_id", nullable = false) private FeasibilityAnalysis feasibilityAnalysis;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Priority priority;
    @Column(nullable = false, length = 200) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    private Integer displayOrder;
}
