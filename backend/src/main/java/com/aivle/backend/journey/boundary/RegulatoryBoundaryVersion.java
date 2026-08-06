package com.aivle.backend.journey.boundary;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.journey.brief.OpportunityBriefVersion;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "regulatory_boundary_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryBoundaryVersion extends BaseEntity {
    public enum Status { READY, NEEDS_INPUT, BLOCKED, FAILED, STALE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "run_id", nullable = false) private RegulatoryBoundaryRun run;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "brief_version_id", nullable = false) private OpportunityBriefVersion briefVersion;
    @Column(nullable = false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(nullable = false, length = 71) private String snapshotHash;
    @Column(nullable = false, length = 71) private String briefSnapshotHash;
    private LocalDateTime staleAt;

    public static RegulatoryBoundaryVersion create(RegulatoryBoundaryRun run, int versionNumber,
            Status status, String snapshotJson, String snapshotHash) {
        if (!java.util.Set.of(RegulatoryBoundaryRun.State.READY,
                RegulatoryBoundaryRun.State.NEEDS_INPUT, RegulatoryBoundaryRun.State.BLOCKED).contains(run.getState())) {
            throw new IllegalStateException("boundary version requires a terminal run");
        }
        if (versionNumber <= 0) throw new IllegalArgumentException("version number must be positive");
        if (snapshotHash == null || !snapshotHash.startsWith("sha256:")) {
            throw new IllegalArgumentException("canonical snapshot hash is required");
        }
        RegulatoryBoundaryVersion value = new RegulatoryBoundaryVersion();
        value.project = run.getProject();
        value.run = run;
        value.briefVersion = run.getBriefVersion();
        value.versionNumber = versionNumber;
        value.status = status;
        value.snapshotJson = snapshotJson;
        value.snapshotHash = snapshotHash;
        value.briefSnapshotHash = run.getInputSnapshotHash();
        return value;
    }

    public void markStale(LocalDateTime now) {
        if (status != Status.STALE) {
            status = Status.STALE;
            staleAt = now;
            run.markStale();
        }
    }
}
