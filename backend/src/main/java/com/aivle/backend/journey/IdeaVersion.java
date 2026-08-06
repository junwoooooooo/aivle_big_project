package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaVersion extends BaseEntity {
    public enum Readiness { UNDER_SPECIFIED, APPROPRIATE, OVER_SPECIFIED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_id", nullable = false) private IdeaSource source;
    @Column(nullable = false) private int versionNumber;
    @Column(nullable = false, columnDefinition = "TEXT") private String normalizedDescription;
    @Column(name = "facts_json", nullable = false, columnDefinition = "TEXT") private String factsJson;
    @Column(name = "assumptions_json", nullable = false, columnDefinition = "TEXT") private String assumptionsJson;
    @Column(name = "constraints_json", nullable = false, columnDefinition = "TEXT") private String constraintsJson;
    @Column(name = "open_questions_json", nullable = false, columnDefinition = "TEXT") private String openQuestionsJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Readiness readiness;
    @Column(nullable = false) private boolean confirmed;

    public static IdeaVersion create(Project project, IdeaSource source, int number, String description,
                                     String facts, String assumptions, String constraints, String questions,
                                     Readiness readiness) {
        IdeaVersion value = new IdeaVersion();
        value.project = project; value.source = source; value.versionNumber = number;
        value.normalizedDescription = description; value.factsJson = facts; value.assumptionsJson = assumptions;
        value.constraintsJson = constraints; value.openQuestionsJson = questions; value.readiness = readiness;
        return value;
    }

    public void confirm() { this.confirmed = true; }
}
