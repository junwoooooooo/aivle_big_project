package com.aivle.backend.aitask.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "ai_task_artifacts",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_ai_task_artifact_job_role",
            columnNames = {"analysis_job_id", "role"}
        ),
        @UniqueConstraint(
            name = "uk_ai_task_artifact_file",
            columnNames = "stored_file_id"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiTaskArtifact extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJob analysisJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_task_result_id")
    private AiTaskResult aiTaskResult;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_file_id", nullable = false)
    private StoredFile storedFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiArtifactRole role;

    private AiTaskArtifact(
        AnalysisJob analysisJob,
        StoredFile storedFile,
        AiArtifactRole role
    ) {
        this.project = analysisJob.getProject();
        this.analysisJob = analysisJob;
        this.storedFile = storedFile;
        this.role = role;
    }

    public static AiTaskArtifact source(
        AnalysisJob analysisJob,
        StoredFile storedFile
    ) {
        return new AiTaskArtifact(
            analysisJob,
            storedFile,
            AiArtifactRole.SOURCE
        );
    }

    public static AiTaskArtifact result(
        AnalysisJob analysisJob,
        AiTaskResult result,
        StoredFile storedFile
    ) {
        AiTaskArtifact artifact = new AiTaskArtifact(
            analysisJob,
            storedFile,
            AiArtifactRole.RESULT
        );
        artifact.aiTaskResult = result;
        return artifact;
    }

    public void attachResult(AiTaskResult result) {
        if (aiTaskResult != null && aiTaskResult != result) {
            throw new IllegalStateException(
                "artifact is already attached to a result"
            );
        }
        aiTaskResult = result;
    }
}
