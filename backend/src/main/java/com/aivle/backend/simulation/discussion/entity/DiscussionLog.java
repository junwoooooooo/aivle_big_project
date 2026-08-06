package com.aivle.backend.simulation.discussion.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.common.entity.Sentiment;
import com.aivle.backend.persona.entity.PersonaInstance;
import com.aivle.backend.simulation.entity.Simulation;
import com.aivle.backend.simulation.entity.SimulationRound;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "discussion_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiscussionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_id", nullable = false)
    private Simulation simulation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = false)
    private PersonaInstance persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id")
    private SimulationRound round;

    private Integer roundNumber;

    private Integer turnNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 50)
    private String roomGroup;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Sentiment sentiment;

    @Column(columnDefinition = "TEXT")
    private String keywordsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_log_id")
    private DiscussionLog replyToLog;
}
