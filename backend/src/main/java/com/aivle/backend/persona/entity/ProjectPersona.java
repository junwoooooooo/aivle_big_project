package com.aivle.backend.persona.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "project_personas", uniqueConstraints = @UniqueConstraint(name = "uk_project_persona", columnNames = {"project_id", "persona_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectPersona extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "persona_id", nullable = false) private PersonaInstance persona;
    @Column(precision = 7, scale = 4) private BigDecimal weight;
    @Column(nullable = false) private Boolean active;
}
