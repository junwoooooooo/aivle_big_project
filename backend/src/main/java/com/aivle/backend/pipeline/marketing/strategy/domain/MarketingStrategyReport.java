package com.aivle.backend.pipeline.marketing.strategy.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "marketing_strategy_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketingStrategyReport extends BaseEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "task_run_id", nullable = false, length = 64)
    private String taskRunId;

    @Column(
        name = "source_manifest_json",
        nullable = false,
        columnDefinition = "TEXT"
    )
    private String sourceManifestJson;

    @Column(
        name = "source_manifest_hash",
        nullable = false,
        length = 71
    )
    private String sourceManifestHash;

    @Column(
        name = "source_json",
        nullable = false,
        columnDefinition = "TEXT"
    )
    private String sourceJson;

    @Column(
        name = "contract_version",
        nullable = false,
        length = 20
    )
    private String contractVersion;

    @Column(
        name = "result_json",
        nullable = false,
        columnDefinition = "TEXT"
    )
    private String resultJson;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public static MarketingStrategyReport create(
        String id,
        Long projectId,
        String taskRunId,
        String sourceManifestJson,
        String sourceManifestHash,
        String sourceJson,
        String resultJson,
        Long userId,
        Instant generatedAt
    ) {
        MarketingStrategyReport report =
            new MarketingStrategyReport();

        report.id = id;
        report.projectId = projectId;
        report.taskRunId = taskRunId;
        report.sourceManifestJson = sourceManifestJson;
        report.sourceManifestHash = sourceManifestHash;
        report.sourceJson = sourceJson;
        report.contractVersion = "marketing-strategy-result-v1";
        report.resultJson = resultJson;
        report.createdByUserId = userId;
        report.generatedAt = generatedAt;

        return report;
    }
}
