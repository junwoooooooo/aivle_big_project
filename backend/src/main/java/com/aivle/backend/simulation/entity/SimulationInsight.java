package com.aivle.backend.simulation.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "simulation_insights")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationInsight extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "simulation_id", nullable = false) private Simulation simulation;
    @Column(nullable = false, length = 100) private String insightType;
    @Column(nullable = false, length = 200) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String supportingDataJson;
    private Integer displayOrder;
}
