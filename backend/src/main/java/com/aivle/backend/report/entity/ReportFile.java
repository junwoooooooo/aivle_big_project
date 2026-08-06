package com.aivle.backend.report.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.file.entity.StoredFile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "report_files")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportFile extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_version_id", nullable = false) private ReportVersion reportVersion;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "stored_file_id", nullable = false) private StoredFile storedFile;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReportFileFormat fileFormat;
}
