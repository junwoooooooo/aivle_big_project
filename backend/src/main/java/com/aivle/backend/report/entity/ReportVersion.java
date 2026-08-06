package com.aivle.backend.report.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "report_versions", uniqueConstraints = @UniqueConstraint(name = "uk_report_version", columnNames = {"report_id", "version_number"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportVersion extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_id", nullable = false) private Report report;
    @Column(nullable = false) private Integer versionNumber;
    @Column(nullable = false, columnDefinition = "TEXT") private String contentJson;
    @Column(length = 100) private String generationModel;
    @Column(columnDefinition = "TEXT") private String sourceSnapshotJson;
}
