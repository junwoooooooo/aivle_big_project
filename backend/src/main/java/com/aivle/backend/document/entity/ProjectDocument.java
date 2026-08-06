package com.aivle.backend.document.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "project_documents")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectDocument extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DocumentType documentType;
    @Column(nullable = false) private Integer currentVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DocumentStatus status;

    private ProjectDocument(Project project, DocumentType documentType) {
        this.project = project;
        this.documentType = documentType;
        this.currentVersion = 0;
        this.status = DocumentStatus.ACTIVE;
    }

    public static ProjectDocument create(Project project, DocumentType documentType) {
        return new ProjectDocument(project, documentType);
    }

    public int allocateNextVersion() {
        this.currentVersion += 1;
        return this.currentVersion;
    }
}
