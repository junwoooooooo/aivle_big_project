package com.aivle.backend.pipeline.finalreport.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "final_report_snapshots", uniqueConstraints =
    @UniqueConstraint(name = "uk_final_report_project_version", columnNames = {"project_id", "version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalReportSnapshot extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "report_version", nullable = false) private int reportVersion;
    @Column(name = "source_manifest_json", nullable = false, columnDefinition = "TEXT") private String sourceManifestJson;
    @Column(name = "source_manifest_hash", nullable = false, length = 71) private String sourceManifestHash;
    @Column(name = "report_json", nullable = false, columnDefinition = "TEXT") private String reportJson;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    @Column(name = "generated_by", nullable = false) private Long generatedBy;

    public static FinalReportSnapshot create(Long projectId, int version, String manifestJson,
            String manifestHash, String reportJson, Instant generatedAt, Long generatedBy) {
        if (projectId == null || version < 1 || blank(manifestJson) || !hash(manifestHash)
                || blank(reportJson) || generatedAt == null || generatedBy == null) {
            throw new IllegalArgumentException("최종 보고서 Snapshot이 올바르지 않습니다.");
        }
        FinalReportSnapshot value = new FinalReportSnapshot();
        value.id = UUID.randomUUID().toString();
        value.projectId = projectId;
        value.reportVersion = version;
        value.sourceManifestJson = manifestJson;
        value.sourceManifestHash = manifestHash;
        value.reportJson = reportJson;
        value.generatedAt = generatedAt;
        value.generatedBy = generatedBy;
        return value;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
