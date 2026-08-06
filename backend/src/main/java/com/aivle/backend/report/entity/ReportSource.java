package com.aivle.backend.report.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "report_sources")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportSource extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_version_id", nullable = false) private ReportVersion reportVersion;
    @Column(nullable = false, length = 50) private String sourceType;
    @Column(nullable = false) private Long sourceId;
}
