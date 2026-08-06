package com.aivle.backend.analysis.feasibility.entity;

import com.aivle.backend.common.entity.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name = "analysis_evidences")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisEvidence extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "feasibility_analysis_id", nullable = false) private FeasibilityAnalysis feasibilityAnalysis;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EvidenceSourceType sourceType;
    @Column(nullable = false, length = 250) private String title;
    @Column(columnDefinition = "TEXT") private String sourceUrl;
    @Column(columnDefinition = "TEXT") private String summary;
    private LocalDateTime retrievedAt;
}
