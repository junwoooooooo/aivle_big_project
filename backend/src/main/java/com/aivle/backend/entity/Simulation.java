package com.aivle.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "simulations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Simulation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 100)
    private String title;

    private Integer totalRounds;

    @Column(length = 20)
    private String status; // "READY", "RUNNING", "COMPLETED"
}
