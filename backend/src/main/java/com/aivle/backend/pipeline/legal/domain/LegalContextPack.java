package com.aivle.backend.pipeline.legal.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
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
    @Column(name = "canonical_context_json", nullable = false, columnDefinition = "TEXT") private String canonicalContextJson;
    @Column(name = "provenance_json", nullable = false, columnDefinition = "TEXT") private String provenanceJson;
    @Column(name = "registry_version", nullable = false, length = 80) private String registryVersion;

    public static LegalContextPack ready(Project project, String snapshotId, String snapshotHash,
            String canonicalContextJson, String provenanceJson, String registryVersion) {
        if (project == null || snapshotId == null || snapshotId.isBlank()
            || snapshotHash == null || !snapshotHash.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("legal context source snapshot is invalid");
        }
        requireJson(canonicalContextJson, "canonical context");
        requireJson(provenanceJson, "provenance");
        if (registryVersion == null || registryVersion.isBlank()) throw new IllegalArgumentException("registry version is required");
        LegalContextPack pack = new LegalContextPack();
        pack.id = UUID.randomUUID().toString();
        pack.project = project;
        pack.sourceSnapshotId = snapshotId;
        pack.sourceSnapshotHash = snapshotHash;
        pack.status = "READY";
        pack.canonicalContextJson = canonicalContextJson;
        pack.provenanceJson = provenanceJson;
        pack.registryVersion = registryVersion;
        return pack;
    }

    private static void requireJson(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
