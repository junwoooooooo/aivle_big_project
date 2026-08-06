package com.aivle.backend.simulation.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.persona.entity.PersonaInstance;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "simulation_personas", uniqueConstraints = @UniqueConstraint(name = "uk_simulation_persona", columnNames = {"simulation_id", "persona_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationPersona extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "simulation_id", nullable = false) private Simulation simulation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "persona_id", nullable = false) private PersonaInstance persona;
    @Column(length = 50) private String role;
    @Column(precision = 7, scale = 4) private BigDecimal weight;
    @Column(columnDefinition = "TEXT") private String profileSnapshotJson;
    @Column(columnDefinition = "TEXT") private String promptSnapshot;
}
