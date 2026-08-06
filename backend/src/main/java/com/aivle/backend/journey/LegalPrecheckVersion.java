package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "legal_precheck_versions") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalPrecheckVersion extends BaseEntity {
    public enum Status { PASS, PASS_WITH_CONDITIONS, REVISION_REQUIRED, PROHIBITED, INSUFFICIENT_INFORMATION, EXPERT_REVIEW_REQUIRED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_origin_version_id", nullable = false) private IdeaOriginVersion ideaOriginVersion;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_run_id", nullable = false) private LegalPrecheckRun sourceRun;
    @Column(nullable = false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Status status;
    @Column(nullable = false, length = 30) private String sourceStatus;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(nullable = false, columnDefinition = "TEXT") private String findingsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String evidenceJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String questionsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String revisionSuggestionsJson;
    @Column(nullable = false) private boolean conceptBuilderAllowed;
    @Column(nullable = false) private boolean sourceVerified;
    @Column(nullable = false, length = 40) private String registryVersion;

    public static LegalPrecheckVersion create(Project project, IdeaOriginVersion origin, LegalPrecheckRun run,
            int number, Status status, String sourceStatus, String summary, String findings, String evidence,
            String questions, String revisions, boolean allowed, boolean verified, String registryVersion) {
        LegalPrecheckVersion value = new LegalPrecheckVersion(); value.project = project; value.ideaOriginVersion = origin;
        value.sourceRun = run; value.versionNumber = number; value.status = status; value.sourceStatus = sourceStatus;
        value.summary = summary; value.findingsJson = findings; value.evidenceJson = evidence;
        value.questionsJson = questions; value.revisionSuggestionsJson = revisions;
        value.conceptBuilderAllowed = allowed; value.sourceVerified = verified; value.registryVersion = registryVersion;
        return value;
    }
}
