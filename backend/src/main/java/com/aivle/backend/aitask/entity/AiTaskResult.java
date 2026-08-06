package com.aivle.backend.aitask.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.job.entity.AnalysisJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "ai_task_results",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ai_task_result_job",
        columnNames = "analysis_job_id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiTaskResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJob analysisJob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private JobType taskType;

    @Column(nullable = false, length = 20)
    private String schemaVersion;

    @Column(nullable = false, length = 100)
    private String requestId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(nullable = false, length = 100)
    private String handler;

    @Column(nullable = false, length = 40)
    private String handlerVersion;

    private AiTaskResult(
        AnalysisJob analysisJob,
        JobType taskType,
        String schemaVersion,
        String requestId,
        String resultJson,
        String handler,
        String handlerVersion
    ) {
        this.analysisJob = analysisJob;
        this.taskType = taskType;
        this.schemaVersion = schemaVersion;
        this.requestId = requestId;
        this.resultJson = resultJson;
        this.handler = handler;
        this.handlerVersion = handlerVersion;
    }

    public static AiTaskResult completed(
        AnalysisJob analysisJob,
        String schemaVersion,
        String requestId,
        String resultJson,
        String handler,
        String handlerVersion
    ) {
        return new AiTaskResult(
            analysisJob,
            analysisJob.getJobType(),
            schemaVersion,
            requestId,
            resultJson,
            handler,
            handlerVersion
        );
    }
}
