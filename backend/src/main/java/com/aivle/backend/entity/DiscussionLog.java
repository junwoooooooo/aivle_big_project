package com.aivle.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "discussion_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscussionLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_id", nullable = false)
    private Simulation simulation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    private Integer roundNum;

    private Integer turnNum;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 50)
    private String roomGroup; // 병렬 소그룹 구분 (e.g., "ROOM_A")
}
