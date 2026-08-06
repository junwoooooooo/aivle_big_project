package com.aivle.backend.project.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.common.entity.ProjectStage;
import com.aivle.backend.common.entity.ProjectStatus;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String industryCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProjectStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private Project(User owner, String title, String description, String industryCategory) {
        this.owner = owner;
        this.title = title;
        this.description = description;
        this.industryCategory = industryCategory;
        this.stage = ProjectStage.DOCUMENT;
        this.status = ProjectStatus.DRAFT;
    }

    public static Project create(User owner, String title, String description, String industryCategory) {
        return new Project(owner, title, description, industryCategory);
    }

    public void updateBasicInfo(String title, String description, String industryCategory) {
        this.title = title;
        this.description = description;
        this.industryCategory = industryCategory;
    }

    public void enterStructuring() {
        if (this.stage == ProjectStage.DOCUMENT) {
            this.stage = ProjectStage.STRUCTURING;
        }
    }

    public void enterLegalReview() {
        if (this.stage != ProjectStage.STRUCTURING) {
            throw new IllegalStateException(
                "only structuring projects can enter legal review"
            );
        }
        this.stage = ProjectStage.LEGAL_REVIEW;
    }

    public void enterFeasibility() {
        if (this.stage != ProjectStage.LEGAL_REVIEW) {
            throw new IllegalStateException("only legal review projects can enter feasibility");
        }
        this.stage = ProjectStage.FEASIBILITY;
    }

    public void enterFinancial() {
        if (this.stage == ProjectStage.FEASIBILITY) {
            this.stage = ProjectStage.FINANCIAL;
        }
    }

    public void enterPersonaConfiguration() {
        if (this.stage == ProjectStage.FINANCIAL) {
            this.stage = ProjectStage.PERSONA_CONFIGURATION;
        }
    }
}
