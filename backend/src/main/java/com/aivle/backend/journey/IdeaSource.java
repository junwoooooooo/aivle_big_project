package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaSource extends BaseEntity {
    public enum SourceType { TEXT, FILE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private SourceType sourceType;
    @Column(length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String originalText;
    @Column(length = 500) private String originalFileReference;

    public static IdeaSource create(Project project, SourceType sourceType, String title, String text, String fileReference) {
        IdeaSource value = new IdeaSource();
        value.project = project;
        value.sourceType = sourceType;
        value.title = title;
        value.originalText = text;
        value.originalFileReference = fileReference;
        return value;
    }
}
