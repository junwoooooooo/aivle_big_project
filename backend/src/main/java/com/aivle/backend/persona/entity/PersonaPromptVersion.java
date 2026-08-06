package com.aivle.backend.persona.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "persona_prompt_versions", uniqueConstraints = @UniqueConstraint(name = "uk_persona_prompt_version", columnNames = {"persona_id", "version_number"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonaPromptVersion extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "persona_id", nullable = false) private PersonaInstance persona;
    @Column(nullable = false) private Integer versionNumber;
    @Column(nullable = false, columnDefinition = "TEXT") private String systemPrompt;
    @Column(length = 100) private String generationModel;
    @Column(columnDefinition = "TEXT") private String parametersJson;
}
