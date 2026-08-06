package com.aivle.backend.validation.panel;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.validation.PersonaValidationTypes.InterviewPurpose;
import com.aivle.backend.validation.PersonaValidationTypes.ValidationStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "persona_panel_interviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PanelInterview extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    @Column(nullable = false, length = 200)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InterviewPurpose purpose;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValidationStatus status;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String personaIdsJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionsJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answersJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceSnapshotJson;
    private LocalDateTime completedAt;

    public static PanelInterview create(
        Project project,
        User actor,
        String title,
        InterviewPurpose purpose,
        String personaIdsJson,
        String questionsJson
    ) {
        PanelInterview value = new PanelInterview();
        value.project = project;
        value.createdBy = actor;
        value.title = title;
        value.purpose = purpose;
        value.status = ValidationStatus.DRAFT;
        value.personaIdsJson = personaIdsJson;
        value.questionsJson = questionsJson;
        value.answersJson = "[]";
        value.summaryJson = "{}";
        value.sourceSnapshotJson = "{}";
        return value;
    }

    public void updateDraft(
        String title,
        InterviewPurpose purpose,
        String personaIdsJson,
        String questionsJson
    ) {
        this.title = title;
        this.purpose = purpose;
        this.personaIdsJson = personaIdsJson;
        this.questionsJson = questionsJson;
        this.status = ValidationStatus.DRAFT;
        this.answersJson = "[]";
        this.summaryJson = "{}";
        this.sourceSnapshotJson = "{}";
        this.completedAt = null;
    }

    public void complete(
        String answersJson,
        String summaryJson,
        String sourceSnapshotJson,
        LocalDateTime now
    ) {
        this.status = ValidationStatus.COMPLETED;
        this.answersJson = answersJson;
        this.summaryJson = summaryJson;
        this.sourceSnapshotJson = sourceSnapshotJson;
        this.completedAt = now;
    }
}
