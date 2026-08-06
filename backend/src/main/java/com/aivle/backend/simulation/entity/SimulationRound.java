package com.aivle.backend.simulation.entity;

import com.aivle.backend.common.entity.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulation_rounds", uniqueConstraints = @UniqueConstraint(name = "uk_simulation_round", columnNames = {"simulation_id", "round_number"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationRound extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "simulation_id", nullable = false) private Simulation simulation;
    @Column(nullable = false) private Integer roundNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private JobStatus status;
    @Column(columnDefinition = "TEXT") private String summary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
