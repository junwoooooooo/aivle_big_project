package com.aivle.backend.simulation.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.common.entity.SimulationType;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "simulations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Simulation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SimulationType simulationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status;

    private Integer totalRounds;
    private Integer currentRound;
    private Integer progress;

    @Column(columnDefinition = "TEXT")
    private String configurationJson;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
