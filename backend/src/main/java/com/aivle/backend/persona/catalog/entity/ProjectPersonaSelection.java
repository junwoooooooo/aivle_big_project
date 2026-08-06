package com.aivle.backend.persona.catalog.entity;

import com.aivle.backend.project.entity.Project;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "project_persona_selections",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_project_persona_selection_project",
        columnNames = "project_id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectPersonaSelection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baseline_persona_id", nullable = false)
    private BaselinePersona persona;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selected_by_user_id", nullable = false)
    private User selectedBy;

    @Column(nullable = false)
    private LocalDateTime selectedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public static ProjectPersonaSelection create(
        Project project,
        BaselinePersona persona,
        User selectedBy,
        LocalDateTime now
    ) {
        ProjectPersonaSelection value = new ProjectPersonaSelection();
        value.project = project;
        value.persona = persona;
        value.selectedBy = selectedBy;
        value.selectedAt = now;
        value.updatedAt = now;
        return value;
    }

    public void select(BaselinePersona persona, User selectedBy, LocalDateTime now) {
        this.persona = persona;
        this.selectedBy = selectedBy;
        this.selectedAt = now;
        this.updatedAt = now;
    }
}
