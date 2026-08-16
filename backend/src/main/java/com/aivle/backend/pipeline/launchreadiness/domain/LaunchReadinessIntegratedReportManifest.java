package com.aivle.backend.pipeline.launchreadiness.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "launch_readiness_integrated_report_manifests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaunchReadinessIntegratedReportManifest extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "selected_modules_json", nullable = false, columnDefinition = "TEXT") private String selectedModulesJson;
    @Column(name = "source_reports_json", nullable = false, columnDefinition = "TEXT") private String sourceReportsJson;
    @Column(name = "generated_by_user_id", nullable = false) private Long generatedByUserId;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    public static LaunchReadinessIntegratedReportManifest create(String id, Long projectId, String modules,
            String sources, Long userId, Instant now) {
        LaunchReadinessIntegratedReportManifest value = new LaunchReadinessIntegratedReportManifest();
        value.id = id; value.projectId = projectId; value.selectedModulesJson = modules;
        value.sourceReportsJson = sources; value.generatedByUserId = userId; value.generatedAt = now;
        return value;
    }
}
