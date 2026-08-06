package com.aivle.backend.analysis.feasibility.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "feasibility_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeasibilityAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false, unique = true)
    private AnalysisJob analysisJob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AnalysisType analysisType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AnalysisDecision decision;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String inputSnapshotJson;

    @Column(columnDefinition = "TEXT")
    private String rawResultJson;

    @Column(length = 100)
    private String modelVersion;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
