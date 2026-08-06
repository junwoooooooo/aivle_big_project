package com.aivle.backend.pipeline.legal.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "legal_context_packs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalContextPack extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @Column(name = "source_snapshot_id", nullable = false, length = 64) private String sourceSnapshotId;
    @Column(name = "source_snapshot_hash", nullable = false, length = 71) private String sourceSnapshotHash;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false, length = 500) private String industry;
    @Column(nullable = false, length = 500) private String region;
    @Column(nullable = false, length = 1000) private String platformRole;
    @Column(nullable = false, columnDefinition = "TEXT") private String transactionStructure;
    @Column(nullable = false, length = 1000) private String payment;
    @Column(nullable = false, length = 1000) private String personalData;
    @Column(nullable = false, columnDefinition = "TEXT") private String physicalActivitiesJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String qualificationsAndPermitsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String labelingAndAdvertisingJson;

    public static LegalContextPack pending(Project project, String snapshotId, String snapshotHash) {
        LegalContextPack pack = new LegalContextPack();
        pack.id = UUID.randomUUID().toString();
        pack.project = project;
        pack.sourceSnapshotId = snapshotId;
        pack.sourceSnapshotHash = snapshotHash;
        pack.status = "PENDING";
        pack.industry = "미확인";
        pack.region = "미확인";
        pack.platformRole = "미확인";
        pack.transactionStructure = "미확인";
        pack.payment = "미확인";
        pack.personalData = "미확인";
        pack.physicalActivitiesJson = "[]";
        pack.qualificationsAndPermitsJson = "[]";
        pack.labelingAndAdvertisingJson = "[]";
        return pack;
    }

    public void complete(String industry, String region, String platformRole, String transactionStructure,
                         String payment, String personalData, String physicalActivitiesJson,
                         String qualificationsAndPermitsJson, String labelingAndAdvertisingJson) {
        this.industry = industry; this.region = region; this.platformRole = platformRole;
        this.transactionStructure = transactionStructure; this.payment = payment; this.personalData = personalData;
        this.physicalActivitiesJson = physicalActivitiesJson; this.qualificationsAndPermitsJson = qualificationsAndPermitsJson;
        this.labelingAndAdvertisingJson = labelingAndAdvertisingJson; this.status = "READY";
    }
}
